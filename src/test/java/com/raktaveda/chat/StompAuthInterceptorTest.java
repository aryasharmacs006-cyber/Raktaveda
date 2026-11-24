package com.raktaveda.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

class StompAuthInterceptorTest {

    private final TokenService tokens = mock(TokenService.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(tokens);
    private final UUID id = UUID.randomUUID();

    private static Message<?> frame(StompCommand command, String destination, Principal user, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompUser user() {
        return new StompUser(new AuthPrincipal(id, PrincipalType.USER, null), "hash");
    }

    @Test
    void connectWithValidUserTokenSetsTheUser() {
        when(tokens.authenticate("good-token")).thenReturn(Optional.of(new AuthPrincipal(id, PrincipalType.USER, null)));
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer good-token"), null);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isInstanceOf(StompUser.class);
        assertThat(accessor.getUser().getName()).isEqualTo("USER:" + id);
    }

    @Test
    void connectWithVerifiedInstitutionIsAllowed() {
        when(tokens.authenticate("inst-token"))
                .thenReturn(Optional.of(new AuthPrincipal(id, PrincipalType.INSTITUTION, "VERIFIED")));
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer inst-token"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void connectWithPendingInstitutionOrAdminIsRefused() {
        when(tokens.authenticate("pending"))
                .thenReturn(Optional.of(new AuthPrincipal(id, PrincipalType.INSTITUTION, "PENDING")));
        when(tokens.authenticate("admin")).thenReturn(Optional.of(new AuthPrincipal(id, PrincipalType.ADMIN, null)));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer pending"), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer admin"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithoutOrWithBadTokenIsRefused() {
        when(tokens.authenticate("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Bearer bad"), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "Token abc"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeOnlyToOwnPrivateQueues() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", user(), null), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/queue/messages", user(), null), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/all", user(), null), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", null, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void sendNeedsAStillValidTokenAndAnAppDestination() {
        when(tokens.stillValid("hash")).thenReturn(true);
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", user(), null), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/queue/messages", user(), null), null))
                .isInstanceOf(MessagingException.class);

        when(tokens.stillValid("hash")).thenReturn(false); // logged out or suspended meanwhile
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", user(), null), null))
                .isInstanceOf(MessagingException.class);
    }
}
