package com.raktaveda.auth;

import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.AuthDtos.StepOneResponse;
import com.raktaveda.auth.AuthDtos.TokenResponse;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.Hashing;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login for INSTITUTION and ADMIN accounts in two steps:
 * 1. email + password (BCrypt check), which sends a one-time code to that email;
 * 2. the code, which returns the login token.
 */
@Service
public class TwoStepAuthService {

    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

    private record Credentials(UUID id, String email, String passwordHash) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final OtpService otp;
    private final EmailSender email;
    private final TokenService tokens;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final RaktavedaProperties props;
    /** Checked when the email is unknown, so "wrong email" and "wrong password" take equally long. */
    private final String dummyHash;

    public TwoStepAuthService(JdbcTemplate jdbc, PasswordEncoder encoder, OtpService otp, EmailSender email,
                              TokenService tokens, AuditService audit, RateLimiter limiter, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.otp = otp;
        this.email = email;
        this.tokens = tokens;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password-used-for-timing");
    }

    public StepOneResponse startLogin(PrincipalType type, String rawEmail, String password) {
        if (type == PrincipalType.USER) {
            throw new IllegalArgumentException("Users log in with a phone number");
        }
        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        // Failures are counted per email address (hashed), so the log and memory never hold the address.
        String failKey = "login-fail:" + type + ":" + Hashing.sha256Hex(normalizedEmail);
        if (limiter.count(failKey, LOCK_WINDOW) >= props.rateLimit().loginFailureLimit()) {
            audit.record("ANONYMOUS", null, "LOGIN_LOCKED", type.name(), null, null);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOCKED", "error.auth.locked");
        }

        Optional<Credentials> found = findCredentials(type, normalizedEmail);
        boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > 72; // BCrypt reads at most 72 bytes
        boolean passwordOk = !tooLong && encoder.matches(password, found.map(Credentials::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            limiter.record(failKey);
            audit.record("ANONYMOUS", null, "LOGIN_FAILED", type.name(), null, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "error.auth.invalid_credentials");
        }

        Credentials account = found.get();
        OtpService.Purpose purpose = type == PrincipalType.INSTITUTION
                ? OtpService.Purpose.INSTITUTION_STEP2 : OtpService.Purpose.ADMIN_STEP2;
        OtpService.Issued issued = otp.issueForPrincipal(purpose, account.id());
        email.sendSecondStepCode(account.email(), issued.code(), CodePurpose.LOGIN);
        audit.record(type.name(), account.id(), "LOGIN_STEP1_OK", type.name(), account.id(), null);
        return new StepOneResponse(issued.challengeId(), otp.ttlSeconds(),
                props.otp().devMode() ? issued.code() : null);
    }

    public TokenResponse completeLogin(UUID challengeId, String code) {
        OtpService.Verified verified = otp.verify(challengeId, code,
                OtpService.Purpose.INSTITUTION_STEP2, OtpService.Purpose.ADMIN_STEP2);
        PrincipalType type = verified.purpose() == OtpService.Purpose.INSTITUTION_STEP2
                ? PrincipalType.INSTITUTION : PrincipalType.ADMIN;
        TokenService.IssuedToken token = tokens.issue(type, verified.principalId());
        audit.record(type.name(), verified.principalId(), "LOGIN_SUCCESS", type.name(), verified.principalId(), null);
        return new TokenResponse(token.token(), token.expiresAt(), type.name());
    }

    private Optional<Credentials> findCredentials(PrincipalType type, String normalizedEmail) {
        String sql = type == PrincipalType.INSTITUTION
                ? "SELECT id, email, password_hash FROM institutions WHERE lower(email) = ?"
                : "SELECT id, email, password_hash FROM admins WHERE lower(email) = ?";
        List<Credentials> rows = jdbc.query(sql,
                (rs, n) -> new Credentials(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                normalizedEmail);
        return rows.stream().findFirst();
    }
}
