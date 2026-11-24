package com.raktaveda.assistant;

// Needs review before real use (medical, legal and privacy). See the class comment below.

import com.raktaveda.assistant.AssistantDtos.AnswerResponse;
import com.raktaveda.common.ApiException;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The AI helper for general questions. It is separate from the rest of the app: it reads no donor, request,
 * stock or chat data and changes none. Steps, in order:
 *
 *   1. Check the question (not empty, at most 300 characters).
 *   2. Check the limits: 5 per minute and 20 per day per account.
 *   3. Safety check BEFORE the AI: emergency, dosage/diagnosis or self-harm words give a fixed reply (no AI call).
 *   4. Remove phone numbers, emails and dates, then ask the AI (10 second limit).
 *   5. Safety check AFTER the AI: a bad reply is replaced by a fixed reply.
 *
 * The question and the answer are never stored and never logged.
 *
 * Needs review before real use (medical, legal and privacy): the approved facts, the word lists, the fixed
 * replies, the limits, and whether sending even cleaned text to an outside AI service is allowed for your users.
 */
@Service
public class AssistantService {

    static final int MAX_QUESTION_CHARS = 300;
    static final int DAILY_LIMIT = 20;
    static final int PER_MINUTE_LIMIT = 5;

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AiClient ai;
    private final ApprovedFacts facts;
    private final AssistantUsageStore usage;
    private final RateLimiter limiter;

    public AssistantService(AiClient ai, ApprovedFacts facts, AssistantUsageStore usage, RateLimiter limiter) {
        this.ai = ai;
        this.facts = facts;
        this.usage = usage;
        this.limiter = limiter;
    }

    public AnswerResponse ask(AuthPrincipal principal, String rawQuestion) {
        if (principal.type() != PrincipalType.USER && principal.type() != PrincipalType.INSTITUTION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "error.forbidden");
        }
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        if (question.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSISTANT_QUESTION_REQUIRED", "error.assistant.question_required");
        }
        if (question.codePointCount(0, question.length()) > MAX_QUESTION_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSISTANT_QUESTION_TOO_LONG", "error.assistant.question_too_long");
        }
        if (!limiter.tryAcquire("assistant:" + principal.id(), PER_MINUTE_LIMIT, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_RATE_LIMITED", "error.assistant.rate_limited");
        }
        AssistantUsageStore.Usage used = usage.tryConsume(principal, question.length(), DAILY_LIMIT);
        if (!used.allowed()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_DAILY_LIMIT", "error.assistant.daily_limit");
        }

        Lang lang = Lang.detect(question);

        Optional<SafetyFilter.Kind> flagged = SafetyFilter.check(question);
        if (flagged.isPresent()) {
            return new AnswerResponse(SafetyFilter.replyFor(flagged.get(), lang), "SAFETY", used.remaining());
        }

        String cleaned = PrivacyCleaner.clean(question);
        String reply;
        try {
            reply = ai.ask(PromptBuilder.systemPrompt(facts.text()), cleaned);
        } catch (RuntimeException e) {
            // Only the kind of failure is logged, never the question, the answer or a stack trace.
            log.warn("Assistant: AI call failed ({})", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE", "error.assistant.unavailable");
        }

        if (!OutputGuard.isSafe(reply)) {
            return new AnswerResponse(SafeReplies.cannotAnswer(lang), "FALLBACK", used.remaining());
        }
        String answer = reply.strip() + "\n\n" + SafeReplies.eligibilityNote(lang);
        return new AnswerResponse(answer, "AI", used.remaining());
    }
}
