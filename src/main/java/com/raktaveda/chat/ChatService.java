package com.raktaveda.chat;

import com.raktaveda.audit.AuditService;
import com.raktaveda.chat.ChatDtos.ConversationView;
import com.raktaveda.chat.ChatDtos.MessageView;
import com.raktaveda.chat.ChatDtos.ReportMessageDto;
import com.raktaveda.common.ApiException;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chat between one user and one verified institution.
 *
 * Who can read what: a conversation is readable ONLY by its two parties. Every method here checks, from
 * the id in the login token (never from the request), that the caller is one of them. A conversation
 * that is not yours looks exactly like one that does not exist (404).
 *
 * Needs review before real use (privacy): message text is stored encrypted and is kept until the
 * account is deleted. Nobody but the two parties can read a conversation. The one exception is a
 * message that someone reports: an admin can then read THAT message only (see AdminService).
 */
@Service
public class ChatService {

    /** Names the database column in the encryption, so the value cannot be moved to another column. */
    public static final String BODY_CONTEXT = "chat_messages.body";
    public static final int MAX_BODY_CHARS = 1000;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private record Convo(UUID id, UUID userId, UUID institutionId) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final RaktavedaProperties props;
    private final SimpMessagingTemplate messaging;

    public ChatService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, AuditService audit,
                       RateLimiter limiter, RaktavedaProperties props, SimpMessagingTemplate messaging) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
        this.messaging = messaging;
    }

    /** A user opens (or re-opens) a conversation with a VERIFIED institution. */
    public ConversationView startConversation(AuthPrincipal user, UUID institutionId) {
        if (institutionId == null) {
            throw validationFailed();
        }
        List<String> names = jdbc.query("SELECT name FROM institutions WHERE id = ? AND status = 'VERIFIED'",
                (rs, n) -> rs.getString("name"), institutionId);
        if (names.isEmpty()) {
            // Same answer for "does not exist" and "not verified".
            throw new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "error.chat.institution_not_found");
        }
        List<ConversationView> existing = jdbc.query(
                "SELECT id, created_at, last_message_at FROM conversations WHERE user_id = ? AND institution_id = ?",
                (rs, n) -> view(rs.getObject("id", UUID.class), names.get(0),
                        rs.getTimestamp("created_at"), rs.getTimestamp("last_message_at")),
                user.id(), institutionId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        if (!limiter.tryAcquire("chat-new:" + user.id(), props.rateLimit().newConversationsPerHour(), Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "error.chat.rate_limited");
        }
        // ON CONFLICT covers two taps at the same moment.
        UUID id = jdbc.queryForObject("""
                INSERT INTO conversations (user_id, institution_id) VALUES (?, ?)
                ON CONFLICT (user_id, institution_id) DO UPDATE SET user_id = EXCLUDED.user_id
                RETURNING id
                """, UUID.class, user.id(), institutionId);
        audit.record("USER", user.id(), "CHAT_STARTED", "CONVERSATION", id, null);
        return new ConversationView(id, names.get(0), Instant.now(), null);
    }

    /** The caller's own conversations, newest activity first. */
    public List<ConversationView> listConversations(AuthPrincipal principal) {
        if (principal.type() == PrincipalType.USER) {
            return jdbc.query("""
                    SELECT c.id, i.name, c.created_at, c.last_message_at
                    FROM conversations c JOIN institutions i ON i.id = c.institution_id
                    WHERE c.user_id = ?
                    ORDER BY coalesce(c.last_message_at, c.created_at) DESC LIMIT 100
                    """, (rs, n) -> view(rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getTimestamp("created_at"), rs.getTimestamp("last_message_at")), principal.id());
        }
        return jdbc.query("""
                SELECT id, created_at, last_message_at FROM conversations
                WHERE institution_id = ?
                ORDER BY coalesce(last_message_at, created_at) DESC LIMIT 100
                """, (rs, n) -> view(rs.getObject("id", UUID.class), null,
                rs.getTimestamp("created_at"), rs.getTimestamp("last_message_at")), principal.id());
    }

    /** Older messages come with "before" (a message id). Newest first, at most 100 at a time. */
    public List<MessageView> history(AuthPrincipal principal, UUID conversationId, Long before, Integer limit) {
        requireParticipant(principal, conversationId);
        int size = Math.min(100, Math.max(1, limit == null ? 50 : limit));
        long beforeId = before == null ? Long.MAX_VALUE : before;
        return jdbc.query("""
                SELECT id, sender_type, body_enc, sent_at FROM chat_messages
                WHERE conversation_id = ? AND id < ?
                ORDER BY id DESC LIMIT ?
                """, (rs, n) -> new MessageView(rs.getLong("id"), conversationId, rs.getString("sender_type"),
                crypto.decrypt(rs.getString("body_enc"), BODY_CONTEXT), rs.getTimestamp("sent_at").toInstant()),
                conversationId, beforeId, size);
    }

    /** Used by both the WebSocket and the REST fallback. Stores the message, then pushes it to both parties. */
    public MessageView send(AuthPrincipal sender, UUID conversationId, String rawBody) {
        if (conversationId == null || rawBody == null) {
            throw validationFailed();
        }
        String body = rawBody.strip();
        if (body.isEmpty() || body.length() > MAX_BODY_CHARS) {
            throw validationFailed();
        }
        Convo convo = requireParticipant(sender, conversationId);
        // Both sides stop at once if the institution is no longer verified (for example, suspended).
        Integer verified = jdbc.queryForObject(
                "SELECT count(*) FROM institutions WHERE id = ? AND status = 'VERIFIED'", Integer.class, convo.institutionId());
        if (verified == null || verified == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INSTITUTION_NOT_VERIFIED", "error.chat.institution_not_verified");
        }
        String side = sender.type().name();
        if (!limiter.tryAcquire("chat:" + side + ":" + sender.id(), props.rateLimit().chatPerMinute(), Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "error.chat.rate_limited");
        }

        MessageView view = tx.execute(t -> {
            MessageView saved = jdbc.queryForObject("""
                    INSERT INTO chat_messages (conversation_id, sender_type, body_enc) VALUES (?, ?, ?)
                    RETURNING id, sent_at
                    """, (rs, n) -> new MessageView(rs.getLong("id"), conversationId, side, body,
                    rs.getTimestamp("sent_at").toInstant()),
                    conversationId, side, crypto.encrypt(body, BODY_CONTEXT));
            jdbc.update("UPDATE conversations SET last_message_at = now() WHERE id = ?", conversationId);
            return saved;
        });

        // The sender gets it too, so their other devices stay in step.
        push(StompUser.name("USER", convo.userId()), view);
        push(StompUser.name("INSTITUTION", convo.institutionId()), view);
        return view;
    }

    /** Reports a message the OTHER party sent. Reporting twice changes nothing. */
    public void report(AuthPrincipal reporter, long messageId, ReportMessageDto dto) {
        List<Object[]> found = jdbc.query("""
                SELECT m.sender_type, c.id, c.user_id, c.institution_id
                FROM chat_messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE m.id = ?
                """, (rs, n) -> new Object[]{rs.getString("sender_type"), rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("institution_id", UUID.class)}, messageId);
        if (found.isEmpty()) {
            throw messageNotFound();
        }
        Object[] row = found.get(0);
        UUID conversationId = (UUID) row[1];
        if (!isMember(reporter, (UUID) row[2], (UUID) row[3])) {
            throw messageNotFound();
        }
        String side = reporter.type().name();
        if (side.equals(row[0])) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN_MESSAGE", "error.chat.cannot_report_own");
        }
        if (!limiter.tryAcquire("chat-report:" + reporter.id(), props.rateLimit().reportsPerHour(), Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "error.chat.rate_limited");
        }
        List<UUID> created = jdbc.query("""
                INSERT INTO chat_reports (message_id, conversation_id, reporter_type, reporter_id, reason, note)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (message_id, reporter_type, reporter_id) DO NOTHING
                RETURNING id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                messageId, conversationId, side, reporter.id(), dto.reason().name(), dto.note());
        if (!created.isEmpty()) {
            // The audit row holds ids and the reason only, never message text.
            audit.record(side, reporter.id(), "CHAT_REPORTED", "REPORT", created.get(0), dto.reason().name());
        }
    }

    // ---- helpers ----

    private Convo requireParticipant(AuthPrincipal principal, UUID conversationId) {
        if (conversationId == null) {
            throw validationFailed();
        }
        List<Convo> rows = jdbc.query("SELECT id, user_id, institution_id FROM conversations WHERE id = ?",
                (rs, n) -> new Convo(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("institution_id", UUID.class)), conversationId);
        if (rows.isEmpty() || !isMember(principal, rows.get(0).userId(), rows.get(0).institutionId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "error.chat.conversation_not_found");
        }
        return rows.get(0);
    }

    private static boolean isMember(AuthPrincipal principal, UUID userId, UUID institutionId) {
        return (principal.type() == PrincipalType.USER && principal.id().equals(userId))
                || (principal.type() == PrincipalType.INSTITUTION && principal.id().equals(institutionId));
    }

    private void push(String userName, MessageView view) {
        try {
            messaging.convertAndSendToUser(userName, "/queue/messages", view);
        } catch (RuntimeException e) {
            // The message is already saved. A failed push must not undo it. Log the type only.
            log.warn("Chat push failed type={}", e.getClass().getSimpleName());
        }
    }

    private static ConversationView view(UUID id, String institutionName, java.sql.Timestamp created, java.sql.Timestamp last) {
        return new ConversationView(id, institutionName, created.toInstant(), last == null ? null : last.toInstant());
    }

    private static ApiException validationFailed() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
    }

    private static ApiException messageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "error.chat.message_not_found");
    }
}
