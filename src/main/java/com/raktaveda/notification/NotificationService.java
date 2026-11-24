package com.raktaveda.notification;

import com.raktaveda.audit.AuditService;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.SqlUtil;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.donor.BloodComponent;
import com.raktaveda.donor.BloodGroup;
import com.raktaveda.donor.BloodGroupCompatibility;
import com.raktaveda.notification.PushNotifier.PushMessage;
import com.raktaveda.notification.PushNotifier.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers phone push tokens and tells matching donors about a new request.
 *
 * Privacy: the push carries only message keys and the request id. It goes through Google's servers when
 * the real FCM notifier is used, so it must never contain a name, phone number, place or blood group.
 * Needs review before real use (privacy): what a push may show on a locked phone screen.
 */
@Service
public class NotificationService {

    /** Names the database column in the encryption, so the value cannot be moved to another column. */
    private static final String TOKEN_CONTEXT = "device_tokens.token";
    private static final int MAX_TOKENS_PER_USER = 5;
    private static final int MAX_RECIPIENT_ROWS = 200;
    // Needs review before real use: a donor gets at most this many request alerts per hour.
    private static final int MAX_ALERTS_PER_DONOR_PER_HOUR = 5;

    private record Target(UUID userId, UUID tokenId, String tokenEnc) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final PushNotifier notifier;
    private final AuditService audit;
    private final com.raktaveda.ratelimit.RateLimiter limiter;
    private final RaktavedaProperties props;

    public NotificationService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, PushNotifier notifier,
                               AuditService audit, com.raktaveda.ratelimit.RateLimiter limiter, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.notifier = notifier;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
    }

    /** Saves a phone's push token for this user. A donor may have up to 5 phones. A token moves to whoever registered it last. */
    public void registerToken(UUID userId, String token) {
        if (!limiter.tryAcquire("device-token:" + userId, 10, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.rate_limited");
        }
        tx.executeWithoutResult(t -> {
            jdbc.update("""
                    INSERT INTO device_tokens (user_id, token_enc, token_index) VALUES (?, ?, ?)
                    ON CONFLICT (token_index) DO UPDATE SET user_id = EXCLUDED.user_id, token_enc = EXCLUDED.token_enc
                    """, userId, crypto.encrypt(token, TOKEN_CONTEXT), crypto.blindIndex(token));
            // Keep only the newest phones.
            jdbc.update("""
                    DELETE FROM device_tokens WHERE user_id = ? AND id NOT IN
                        (SELECT id FROM device_tokens WHERE user_id = ? ORDER BY created_at DESC LIMIT ?)
                    """, userId, userId, MAX_TOKENS_PER_USER);
        });
        audit.record("USER", userId, "DEVICE_TOKEN_REGISTERED", "USER", userId, null); // never the token itself
    }

    /** Stops push notifications to one phone. Also how a donor opts out. */
    public void unregisterToken(UUID userId, String token) {
        jdbc.update("DELETE FROM device_tokens WHERE user_id = ? AND token_index = ?", userId, crypto.blindIndex(token));
        audit.record("USER", userId, "DEVICE_TOKEN_REMOVED", "USER", userId, null);
    }

    /**
     * Alerts eligible, available donors within 25 km whose group can help. One PostGIS query picks the
     * recipients (nearest first, at most 200 phones); the loop below only sends. A failure here must never
     * undo the request, so the caller wraps this in a try/catch.
     */
    public void notifyNewRequest(UUID requestId) {
        List<Object[]> request = jdbc.query(
                "SELECT blood_group, component FROM blood_requests WHERE id = ? AND status = 'OPEN' AND location IS NOT NULL",
                (rs, n) -> new Object[]{BloodGroup.fromLabel(rs.getString("blood_group")),
                        BloodComponent.valueOf(rs.getString("component"))}, requestId);
        if (request.isEmpty()) {
            return;
        }
        BloodGroup recipient = (BloodGroup) request.get(0)[0];
        BloodComponent component = (BloodComponent) request.get(0)[1];
        List<BloodGroup> donorGroups = BloodGroupCompatibility.donorGroupsFor(component, recipient);

        List<Object> args = new ArrayList<>();
        args.add(requestId);
        donorGroups.forEach(g -> args.add(g.label()));
        args.add(props.eligibility().minWeightKg());
        args.add(props.eligibility().minDaysBetweenDonations());
        args.add(MAX_RECIPIENT_ROWS);
        String sql = """
                SELECT dp.user_id, dt.id AS token_id, dt.token_enc
                FROM blood_requests r
                JOIN donor_profiles dp ON dp.available AND dp.location IS NOT NULL
                                      AND ST_DWithin(dp.location, r.location, 25000)
                JOIN device_tokens dt ON dt.user_id = dp.user_id
                WHERE r.id = ? AND r.location IS NOT NULL
                  AND dp.blood_group IN (""" + SqlUtil.placeholders(donorGroups.size()) + """
                )
                  AND dp.age_ok AND dp.weight_kg >= ?
                  AND (dp.last_donation_date IS NULL OR dp.last_donation_date <= current_date - (?::int))
                  AND NOT (r.requester_type = 'USER' AND r.requester_id = dp.user_id)
                ORDER BY ST_Distance(dp.location, r.location)
                LIMIT ?
                """;
        List<Target> targets = jdbc.query(sql, (rs, n) -> new Target(rs.getObject("user_id", UUID.class),
                rs.getObject("token_id", UUID.class), rs.getString("token_enc")), args.toArray());

        PushMessage message = new PushMessage("NEW_REQUEST", "notification.request.new.title",
                "notification.request.new.body", Map.of("type", "NEW_REQUEST", "requestId", requestId.toString()));
        Set<UUID> allowed = new HashSet<>();
        Set<UUID> blocked = new HashSet<>();
        int sent = 0;
        for (Target target : targets) {
            if (blocked.contains(target.userId())) {
                continue;
            }
            if (!allowed.contains(target.userId())) {
                if (limiter.tryAcquire("notify:" + target.userId(), MAX_ALERTS_PER_DONOR_PER_HOUR, Duration.ofHours(1))) {
                    allowed.add(target.userId());
                } else {
                    blocked.add(target.userId());
                    continue;
                }
            }
            Result result;
            try {
                result = notifier.send(crypto.decrypt(target.tokenEnc(), TOKEN_CONTEXT), message);
            } catch (RuntimeException e) {
                result = Result.FAILED; // one bad phone must not stop the others
            }
            if (result == Result.SENT) {
                sent++;
            } else if (result == Result.INVALID_TOKEN) {
                jdbc.update("DELETE FROM device_tokens WHERE id = ?", target.tokenId());
            }
        }
        // Only a count is audited, never who was notified.
        audit.record("SYSTEM", null, "NOTIFICATIONS_SENT", "REQUEST", requestId, "count=" + sent);
    }

    /** The development log keeps 30 days. */
    @Scheduled(cron = "0 45 3 * * *")
    public void purgeLog() {
        jdbc.update("DELETE FROM notification_log WHERE sent_at < now() - interval '30 days'");
    }
}
