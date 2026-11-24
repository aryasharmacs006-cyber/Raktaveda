package com.raktaveda.auth;

import com.raktaveda.audit.AuditService;
import com.raktaveda.common.ApiException;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time codes for both user login (SMS) and the institution/admin second step (email).
 *
 * Rules: the code is stored only as a keyed hash; it expires after the configured minutes
 * (5); only 3 tries are allowed, then it is locked; at most 3 codes may be requested per
 * phone (or per account) in 10 minutes; a new code cancels the older one.
 * Needs review before real use: these numbers are a policy decision.
 */
@Service
public class OtpService {

    public enum Purpose { USER_LOGIN, INSTITUTION_STEP2, ADMIN_STEP2, USER_DELETE, INSTITUTION_DELETE }

    public record Issued(UUID challengeId, String code) {
    }

    public record Verified(Purpose purpose, UUID principalId, String phoneIndex, String phoneEnc) {
    }

    private record Row(Purpose purpose, UUID principalId, String phoneIndex, String phoneEnc, String codeHash, int attempts) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final RaktavedaProperties props;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public OtpService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto,
                      RaktavedaProperties props, AuditService audit) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.props = props;
        this.audit = audit;
    }

    public int ttlSeconds() {
        return props.otp().ttlMinutes() * 60;
    }

    /** New code for a phone login. phoneEnc is the phone number encrypted with context "users.phone". */
    public Issued issueForPhone(String phoneIndex, String phoneEnc) {
        return issueForPhone(Purpose.USER_LOGIN, phoneIndex, phoneEnc);
    }

    /**
     * New code sent to a phone, for login (USER_LOGIN) or for account deletion (USER_DELETE).
     * Each purpose is checked separately when the code comes back, so a login code can never confirm a deletion.
     */
    public Issued issueForPhone(Purpose purpose, String phoneIndex, String phoneEnc) {
        return tx.execute(status -> {
            lock("otp-phone:" + phoneIndex);
            Integer recent = jdbc.queryForObject(
                    "SELECT count(*) FROM otp_challenges WHERE phone_index = ? AND created_at > now() - ? * interval '1 minute'",
                    Integer.class, phoneIndex, props.otp().requestWindowMinutes());
            if (recent != null && recent >= props.otp().maxRequestsPerPhone()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "error.otp.rate_limited");
            }
            jdbc.update("UPDATE otp_challenges SET consumed_at = now(), phone_enc = NULL WHERE phone_index = ? AND consumed_at IS NULL",
                    phoneIndex);
            return insert(purpose, phoneIndex, phoneEnc, null);
        });
    }

    /** New second-step code for an institution or admin account. */
    public Issued issueForPrincipal(Purpose purpose, UUID principalId) {
        return tx.execute(status -> {
            lock("otp-principal:" + principalId);
            Integer recent = jdbc.queryForObject(
                    "SELECT count(*) FROM otp_challenges WHERE principal_id = ? AND created_at > now() - ? * interval '1 minute'",
                    Integer.class, principalId, props.otp().requestWindowMinutes());
            if (recent != null && recent >= props.otp().maxRequestsPerPhone()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED", "error.otp.rate_limited");
            }
            jdbc.update("UPDATE otp_challenges SET consumed_at = now() WHERE principal_id = ? AND purpose = ? AND consumed_at IS NULL",
                    principalId, purpose.name());
            return insert(purpose, null, null, principalId);
        });
    }

    /**
     * Checks a code. Each try is counted by the database in the same statement that reads
     * the challenge, so many requests sent at once cannot get extra guesses.
     * Throws OTP_EXPIRED (unknown, expired, used or already locked) / OTP_INVALID / OTP_LOCKED.
     */
    public Verified verify(UUID challengeId, String code, Purpose first, Purpose second) {
        List<Row> rows = jdbc.query("""
                UPDATE otp_challenges SET failed_attempts = failed_attempts + 1
                WHERE id = ? AND purpose IN (?, ?) AND consumed_at IS NULL
                  AND expires_at > now() AND failed_attempts < ?
                RETURNING purpose, principal_id, phone_index, phone_enc, code_hash, failed_attempts
                """,
                (rs, n) -> new Row(
                        Purpose.valueOf(rs.getString("purpose")),
                        rs.getObject("principal_id", UUID.class),
                        rs.getString("phone_index"),
                        rs.getString("phone_enc"),
                        rs.getString("code_hash"),
                        rs.getInt("failed_attempts")),
                challengeId, first.name(), second.name(), props.otp().maxWrongAttempts());
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_EXPIRED", "error.otp.expired");
        }
        Row row = rows.get(0);
        String expected = crypto.codeHash(challengeId.toString(), code);
        if (!crypto.constantTimeEquals(expected, row.codeHash())) {
            if (row.attempts() >= props.otp().maxWrongAttempts()) {
                jdbc.update("UPDATE otp_challenges SET phone_enc = NULL WHERE id = ?", challengeId);
                audit.record("ANONYMOUS", null, "OTP_LOCKED", "OTP_CHALLENGE", challengeId, null);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "error.otp.locked");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "error.otp.invalid");
        }
        // Single use: only one request can ever flip consumed_at from NULL.
        int used = jdbc.update("UPDATE otp_challenges SET consumed_at = now(), phone_enc = NULL WHERE id = ? AND consumed_at IS NULL",
                challengeId);
        if (used != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_EXPIRED", "error.otp.expired");
        }
        return new Verified(row.purpose(), row.principalId(), row.phoneIndex(), row.phoneEnc());
    }

    /** Old challenges (and any leftover encrypted phone numbers) are deleted after an hour. */
    @Scheduled(fixedDelay = 600_000)
    public void purge() {
        jdbc.update("DELETE FROM otp_challenges WHERE created_at < now() - interval '1 hour'");
    }

    private Issued insert(Purpose purpose, String phoneIndex, String phoneEnc, UUID principalId) {
        UUID id = UUID.randomUUID();
        String code = generateCode();
        jdbc.update("""
                INSERT INTO otp_challenges (id, purpose, phone_index, phone_enc, principal_id, code_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, now() + ? * interval '1 second')
                """,
                id, purpose.name(), phoneIndex, phoneEnc, principalId,
                crypto.codeHash(id.toString(), code), ttlSeconds());
        return new Issued(id, code);
    }

    private String generateCode() {
        int length = props.otp().length();
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", random.nextInt(bound));
    }

    /** Makes two requests for the same key wait for each other, so the "3 per 10 minutes" count is exact. */
    private void lock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", (RowCallbackHandler) rs -> { }, key);
    }
}
