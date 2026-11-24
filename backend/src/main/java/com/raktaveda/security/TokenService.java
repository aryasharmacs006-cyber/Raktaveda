package com.raktaveda.security;

import com.raktaveda.common.Hashing;
import com.raktaveda.config.RaktavedaProperties;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Login tokens. A token is 32 random bytes. The user gets the token once; the database
 * keeps only its SHA-256 hash, so a stolen database cannot be used to log in.
 * (Plain SHA-256 is fine here because the token is long and random, unlike a 6-digit code.)
 */
@Service
public class TokenService {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final RaktavedaProperties props;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JdbcTemplate jdbc, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public IssuedToken issue(PrincipalType type, UUID principalId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = Instant.now().plus(ttlHours(type), ChronoUnit.HOURS);
        jdbc.update("INSERT INTO auth_tokens (token_hash, principal_type, principal_id, expires_at) VALUES (?, ?, ?, ?)",
                Hashing.sha256Hex(token), type.name(), principalId, Timestamp.from(expires));
        return new IssuedToken(token, expires);
    }

    /** Returns the account for a token, or empty if the token is unknown, expired or revoked. */
    public Optional<AuthPrincipal> authenticate(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            return Optional.empty();
        }
        // The account must still exist (a deleted account stops working at once).
        List<AuthPrincipal> found = jdbc.query("""
                SELECT t.principal_type, t.principal_id, i.status AS institution_status
                FROM auth_tokens t
                LEFT JOIN institutions i ON t.principal_type = 'INSTITUTION' AND i.id = t.principal_id
                WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > now()
                  AND CASE t.principal_type
                        WHEN 'USER'  THEN EXISTS (SELECT 1 FROM users u WHERE u.id = t.principal_id)
                        WHEN 'ADMIN' THEN EXISTS (SELECT 1 FROM admins a WHERE a.id = t.principal_id)
                        ELSE i.id IS NOT NULL
                      END
                """,
                (rs, n) -> new AuthPrincipal(
                        rs.getObject("principal_id", UUID.class),
                        PrincipalType.valueOf(rs.getString("principal_type")),
                        rs.getString("institution_status")),
                Hashing.sha256Hex(token));
        return found.stream().findFirst();
    }

    /** Is this token (given as its hash) still unexpired and not revoked? Used to re-check open chat connections. */
    public boolean stillValid(String tokenHash) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM auth_tokens WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()",
                Integer.class, tokenHash);
        return n != null && n > 0;
    }

    public void revoke(String token) {
        jdbc.update("UPDATE auth_tokens SET revoked_at = now() WHERE token_hash = ? AND revoked_at IS NULL",
                Hashing.sha256Hex(token));
    }

    public void revokeAllFor(PrincipalType type, UUID principalId) {
        jdbc.update("UPDATE auth_tokens SET revoked_at = now() WHERE principal_type = ? AND principal_id = ? AND revoked_at IS NULL",
                type.name(), principalId);
    }

    /** Deletes tokens that expired more than a week ago. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        jdbc.update("DELETE FROM auth_tokens WHERE expires_at < now() - interval '7 days'");
    }

    private int ttlHours(PrincipalType type) {
        return switch (type) {
            case USER -> props.tokens().userTtlHours();
            case INSTITUTION -> props.tokens().institutionTtlHours();
            case ADMIN -> props.tokens().adminTtlHours();
        };
    }
}
