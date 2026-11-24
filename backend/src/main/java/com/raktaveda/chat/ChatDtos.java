package com.raktaveda.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for chat. Plain DTOs, never database rows. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record StartConversationDto(@NotNull UUID institutionId) {
    }

    /** Used by the REST fallback. The WebSocket version is SocketSendDto, which also names the conversation. */
    public record SendMessageDto(@NotBlank @Size(max = 1000) String body) {
    }

    /** Sent to /app/chat.send over the WebSocket. Checked again inside ChatService. */
    public record SocketSendDto(UUID conversationId, String body) {
    }

    /** institutionName is filled for a user's list. An institution's list leaves it out, because the user stays anonymous. */
    public record ConversationView(UUID id, String institutionName, Instant createdAt, Instant lastMessageAt) {
    }

    /** sender is "USER" or "INSTITUTION". The text is plain text: the app must display it as text, never as HTML. */
    public record MessageView(long id, UUID conversationId, String sender, String body, Instant sentAt) {
        @Override
        public String toString() {
            return "MessageView[redacted]"; // never print message text by accident
        }
    }

    public record ReportMessageDto(@NotNull ReportReason reason, @Size(max = 300) String note) {
    }
}
