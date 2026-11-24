package com.raktaveda.chat;

import com.raktaveda.chat.ChatDtos.SocketSendDto;
import com.raktaveda.common.ApiError;
import com.raktaveda.common.ApiException;
import java.security.Principal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/** The WebSocket entry point for chat: clients SEND to /app/chat.send. */
@Controller
public class ChatSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketController.class);

    private final ChatService chat;

    public ChatSocketController(ChatService chat) {
        this.chat = chat;
    }

    /** The message is delivered by ChatService to both parties on /user/queue/messages. */
    @MessageMapping("/chat.send")
    public void send(@Payload SocketSendDto dto, Principal principal) {
        // StompAuthInterceptor guarantees this is a StompUser, so the caller is always a logged-in account.
        StompUser user = (StompUser) principal;
        chat.send(user.principal(), dto.conversationId(), dto.body());
    }

    /** Errors come back, in the standard error format, only to the connection that caused them. */
    @MessageExceptionHandler
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ApiError handleError(Exception ex) {
        if (ex instanceof ApiException api) {
            return new ApiError(api.getCode(), api.getMessage(), Instant.now());
        }
        log.error("Chat socket error type={}", ex.getClass().getName()); // type only, never the message
        return new ApiError("INTERNAL_ERROR", "error.internal", Instant.now());
    }
}
