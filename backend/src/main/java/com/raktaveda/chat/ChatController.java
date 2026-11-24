package com.raktaveda.chat;

import com.raktaveda.chat.ChatDtos.ConversationView;
import com.raktaveda.chat.ChatDtos.MessageView;
import com.raktaveda.chat.ChatDtos.ReportMessageDto;
import com.raktaveda.chat.ChatDtos.SendMessageDto;
import com.raktaveda.chat.ChatDtos.StartConversationDto;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat over plain HTTP: start a conversation, read history, send a message (a fallback for when
 * the WebSocket is not connected, and handy for testing in Swagger), and report a message.
 * Live delivery is over the WebSocket, see WebSocketConfig.
 */
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasAnyRole('USER', 'INSTITUTION_VERIFIED')")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** Only a user can start a conversation, so an institution never learns who a user is. */
    @PostMapping("/conversations")
    @PreAuthorize("hasRole('USER')")
    public ConversationView start(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody StartConversationDto body) {
        return chat.startConversation(principal, body.institutionId());
    }

    @GetMapping("/conversations")
    public List<ConversationView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return chat.listConversations(principal);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageView> history(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(required = false) Integer limit) {
        return chat.history(principal, id, before, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                            @Valid @RequestBody SendMessageDto body) {
        return chat.send(principal, id, body.body());
    }

    @PostMapping("/messages/{messageId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long messageId,
                       @Valid @RequestBody ReportMessageDto body) {
        chat.report(principal, messageId, body);
    }
}
