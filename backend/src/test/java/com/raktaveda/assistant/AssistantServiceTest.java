package com.raktaveda.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raktaveda.assistant.AssistantDtos.AnswerResponse;
import com.raktaveda.common.ApiException;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AssistantServiceTest {

    private AiClient ai;
    private AssistantUsageStore usage;
    private AssistantService service;
    private final AuthPrincipal user = new AuthPrincipal(UUID.randomUUID(), PrincipalType.USER, null);

    @BeforeEach
    void setUp() {
        ai = mock(AiClient.class);
        usage = mock(AssistantUsageStore.class);
        when(usage.tryConsume(eq(user), anyInt(), anyInt())).thenReturn(new AssistantUsageStore.Usage(true, 19));
        service = new AssistantService(ai, new ApprovedFacts("FACT: the blood bank decides eligibility."), usage, new RateLimiter());
    }

    @Test
    void normalQuestionGoesToTheAiAndGetsTheEligibilityNote() {
        when(ai.ask(anyString(), anyString())).thenReturn("Eat and drink normally.");
        AnswerResponse r = service.ask(user, "What should I do before donating?");
        assertEquals("AI", r.source());
        assertTrue(r.answer().startsWith("Eat and drink normally."));
        assertTrue(r.answer().contains("final decision on eligibility"));
        assertEquals(19, r.remainingToday());
    }

    @Test
    void aiNeverSeesPhoneNumbersEmailsOrDatesAndGetsTheFacts() {
        when(ai.ask(anyString(), anyString())).thenReturn("Fine.");
        service.ask(user, "My number is 9876543210, mail a@b.com, born 15/06/1992. Can I donate?");
        verify(ai).ask(contains("FACT: the blood bank decides eligibility."),
                eq("My number is [removed], mail [removed], born [removed]. Can I donate?"));
    }

    @Test
    void emergencyQuestionDoesNotCallTheAi() {
        AnswerResponse r = service.ask(user, "He is unconscious");
        assertEquals("SAFETY", r.source());
        assertTrue(r.answer().contains("112"));
        verify(ai, never()).ask(anyString(), anyString());
    }

    @Test
    void badAiReplyIsReplacedByFixedReply() {
        when(ai.ask(anyString(), anyString())).thenReturn("Call 98765 43210 now.");
        assertEquals("FALLBACK", service.ask(user, "Where is a blood bank?").source());
        when(ai.ask(anyString(), anyString())).thenReturn("The app decides if you are eligible.");
        assertEquals("FALLBACK", service.ask(user, "Am I good to go?").source());
    }

    @Test
    void aiFailureGivesErrorCodeNotStackTrace() {
        when(ai.ask(anyString(), anyString())).thenThrow(new AiUnavailableException("timeout"));
        ApiException e = assertThrows(ApiException.class, () -> service.ask(user, "What is blood donation?"));
        assertEquals("ASSISTANT_UNAVAILABLE", e.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    @Test
    void tooLongOrEmptyQuestionsAreRejectedBeforeAnythingElse() {
        ApiException tooLong = assertThrows(ApiException.class, () -> service.ask(user, "a".repeat(301)));
        assertEquals("ASSISTANT_QUESTION_TOO_LONG", tooLong.getCode());
        ApiException empty = assertThrows(ApiException.class, () -> service.ask(user, "  "));
        assertEquals("ASSISTANT_QUESTION_REQUIRED", empty.getCode());
        service.ask(user, "a".repeat(300)); // exactly 300 is allowed (AI mock returns null -> fallback)
        verify(usage).tryConsume(eq(user), eq(300), anyInt());
    }

    @Test
    void dailyLimitGives429() {
        when(usage.tryConsume(eq(user), anyInt(), anyInt())).thenReturn(new AssistantUsageStore.Usage(false, 0));
        ApiException e = assertThrows(ApiException.class, () -> service.ask(user, "Hello?"));
        assertEquals("ASSISTANT_DAILY_LIMIT", e.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }

    @Test
    void sixthQuestionInAMinuteGives429() {
        when(ai.ask(anyString(), anyString())).thenReturn("Fine.");
        for (int i = 0; i < 5; i++) {
            service.ask(user, "What is blood donation?");
        }
        ApiException e = assertThrows(ApiException.class, () -> service.ask(user, "What is blood donation?"));
        assertEquals("ASSISTANT_RATE_LIMITED", e.getCode());
    }

    @Test
    void adminsAreRefused() {
        AuthPrincipal admin = new AuthPrincipal(UUID.randomUUID(), PrincipalType.ADMIN, null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.ask(admin, "Hello?")).getStatus());
    }
}
