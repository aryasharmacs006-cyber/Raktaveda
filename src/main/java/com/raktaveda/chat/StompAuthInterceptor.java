package com.raktaveda.chat;

import com.raktaveda.common.Hashing;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Guards every WebSocket frame sent by a client.
 *
 * - CONNECT: must carry "Authorization: Bearer <token>", the same token as the REST API. Only users and
 *   VERIFIED institutions may connect. The token is sent in this frame, never in the URL.
 * - SUBSCRIBE: only to the caller's own private queues ("/user/queue/..."). Anything else is refused.
 * - SEND: only to "/app/..." and only while the token is still valid (so logging out or an admin
 *   suspending an institution stops an already open connection).
 *
 * Any failure closes the connection with a STOMP ERROR frame.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final TokenService tokens;

    public StompAuthInterceptor(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            connect(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            requireUser(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/user/queue/")) {
                throw denied();
            }
        } else if (command == StompCommand.SEND) {
            StompUser user = requireUser(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/app/") || !tokens.stillValid(user.tokenHash())) {
                throw denied();
            }
        }
        return message;
    }

    private void connect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw denied();
        }
        String token = header.substring(7).trim();
        AuthPrincipal principal = tokens.authenticate(token).orElseThrow(StompAuthInterceptor::denied);
        boolean allowed = principal.type() == PrincipalType.USER
                || (principal.type() == PrincipalType.INSTITUTION && "VERIFIED".equals(principal.institutionStatus()));
        if (!allowed) {
            throw denied();
        }
        accessor.setUser(new StompUser(principal, Hashing.sha256Hex(token)));
    }

    private static StompUser requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof StompUser stompUser) {
            return stompUser;
        }
        throw denied();
    }

    private static MessagingException denied() {
        return new MessagingException("denied"); // deliberately says nothing about why
    }
}
