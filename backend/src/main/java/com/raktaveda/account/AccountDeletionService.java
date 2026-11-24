package com.raktaveda.account;

import com.raktaveda.account.AccountDtos.DeleteStartResponse;
import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.CodePurpose;
import com.raktaveda.auth.EmailSender;
import com.raktaveda.auth.OtpService;
import com.raktaveda.auth.SmsSender;
import com.raktaveda.auth.UserAuthService;
import com.raktaveda.common.ApiException;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.ratelimit.RateLimiter;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.PrincipalType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes an account and its personal data after TWO confirmations:
 *
 *   1. START: the person asks to delete. A user is sent a fresh one-time code by SMS. An institution must
 *      type its password and is sent a fresh code by email. So a stolen login token alone cannot delete an account.
 *      These codes have their own purpose (USER_DELETE, INSTITUTION_DELETE), so a login code cannot be reused here.
 *   2. CONFIRM: the person sends that code AND the word DELETE. Only then is anything removed.
 *
 * What is removed: the account, the donor profile, push tokens, login tokens, pending codes, the requests
 * the account posted, its responses to requests, and every conversation and message (with any reports on them).
 * The account's phone number and date of birth are encrypted columns inside rows that are deleted.
 *
 * Needs review before real use (privacy and legal):
 * - The audit log keeps rows with the account's random id (for accountability). The id no longer points to anyone.
 * - Phone numbers already shown to a requester after a donor accepted cannot be taken back.
 * - Reports about a conversation are deleted with it. An institution could delete itself to remove a report
 *   against it. Decide whether reports must be kept for a period.
 * - Admin accounts cannot be deleted here (they exist only in environment settings).
 */
@Service
public class AccountDeletionService {

    private static final Duration START_WINDOW = Duration.ofHours(1);
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(15);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final OtpService otp;
    private final SmsSender sms;
    private final EmailSender email;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final RaktavedaProperties props;

    public AccountDeletionService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, OtpService otp,
                                  SmsSender sms, EmailSender email, PasswordEncoder encoder, AuditService audit,
                                  RateLimiter limiter, RaktavedaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.otp = otp;
        this.sms = sms;
        this.email = email;
        this.encoder = encoder;
        this.audit = audit;
        this.limiter = limiter;
        this.props = props;
    }

    /** Confirmation 1. Sends a fresh code. Nothing is deleted yet. */
    public DeleteStartResponse start(AuthPrincipal principal, String password) {
        rejectAdmin(principal);
        if (!limiter.tryAcquire("acct-delete:" + principal.id(), 3, START_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.rate_limited");
        }
        OtpService.Issued issued;
        if (principal.type() == PrincipalType.USER) {
            List<String[]> rows = jdbc.query("SELECT phone_index, phone_enc FROM users WHERE id = ?",
                    (rs, n) -> new String[]{rs.getString("phone_index"), rs.getString("phone_enc")}, principal.id());
            if (rows.isEmpty()) {
                throw accountNotFound();
            }
            issued = otp.issueForPhone(OtpService.Purpose.USER_DELETE, rows.get(0)[0], rows.get(0)[1]);
            sms.sendOtp(crypto.decrypt(rows.get(0)[1], UserAuthService.PHONE_CONTEXT), issued.code(),
                    CodePurpose.ACCOUNT_DELETION);
        } else {
            issued = startInstitution(principal, password);
        }
        audit.record(principal.type().name(), principal.id(), "ACCOUNT_DELETION_STARTED", principal.type().name(),
                principal.id(), null);
        return new DeleteStartResponse(issued.challengeId(), otp.ttlSeconds(),
                props.otp().devMode() ? issued.code() : null);
    }

    /** Confirmation 2. Checks the code, then deletes. The word DELETE is checked by the request validation. */
    public void confirm(AuthPrincipal principal, UUID challengeId, String code) {
        rejectAdmin(principal);
        if (principal.type() == PrincipalType.USER) {
            OtpService.Verified verified = otp.verify(challengeId, code,
                    OtpService.Purpose.USER_DELETE, OtpService.Purpose.USER_DELETE);
            List<String> index = jdbc.query("SELECT phone_index FROM users WHERE id = ?",
                    (rs, n) -> rs.getString(1), principal.id());
            if (index.isEmpty()) {
                throw accountNotFound();
            }
            // The code must have been sent to THIS account's phone.
            if (!index.get(0).equals(verified.phoneIndex())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "error.otp.invalid");
            }
            deleteUser(principal.id(), index.get(0));
        } else {
            OtpService.Verified verified = otp.verify(challengeId, code,
                    OtpService.Purpose.INSTITUTION_DELETE, OtpService.Purpose.INSTITUTION_DELETE);
            if (!principal.id().equals(verified.principalId())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "error.otp.invalid");
            }
            deleteInstitution(principal.id());
        }
    }

    // ---- helpers ----

    private OtpService.Issued startInstitution(AuthPrincipal principal, String password) {
        if (password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
        }
        String failKey = "acct-delete-fail:" + principal.id();
        if (limiter.count(failKey, FAIL_WINDOW) >= props.rateLimit().loginFailureLimit()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOCKED", "error.auth.locked");
        }
        List<String[]> rows = jdbc.query("SELECT email, password_hash FROM institutions WHERE id = ?",
                (rs, n) -> new String[]{rs.getString("email"), rs.getString("password_hash")}, principal.id());
        if (rows.isEmpty()) {
            throw accountNotFound();
        }
        boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > 72; // BCrypt reads at most 72 bytes
        if (tooLong || !encoder.matches(password, rows.get(0)[1])) {
            limiter.record(failKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "error.auth.invalid_credentials");
        }
        OtpService.Issued issued = otp.issueForPrincipal(OtpService.Purpose.INSTITUTION_DELETE, principal.id());
        email.sendSecondStepCode(rows.get(0)[0], issued.code(), CodePurpose.ACCOUNT_DELETION);
        return issued;
    }

    private void deleteUser(UUID userId, String phoneIndex) {
        tx.executeWithoutResult(t -> {
            // Requests have no foreign key to their owner (it can be a user or an institution), so delete them here.
            // Their responses go with them. Everything else hangs off the users row and is removed by it.
            jdbc.update("DELETE FROM blood_requests WHERE requester_type = 'USER' AND requester_id = ?", userId);
            jdbc.update("DELETE FROM auth_tokens WHERE principal_type = 'USER' AND principal_id = ?", userId);
            jdbc.update("DELETE FROM otp_challenges WHERE phone_index = ?", phoneIndex);
            // Cascades to: donor_profiles, device_tokens, request_responses, conversations, chat_messages, chat_reports.
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            audit.record("USER", userId, "ACCOUNT_DELETED", "USER", userId, null);
        });
    }

    private void deleteInstitution(UUID institutionId) {
        tx.executeWithoutResult(t -> {
            jdbc.update("DELETE FROM blood_requests WHERE requester_type = 'INSTITUTION' AND requester_id = ?", institutionId);
            jdbc.update("DELETE FROM auth_tokens WHERE principal_type = 'INSTITUTION' AND principal_id = ?", institutionId);
            jdbc.update("DELETE FROM otp_challenges WHERE principal_id = ?", institutionId);
            // Cascades to: conversations, chat_messages, chat_reports.
            jdbc.update("DELETE FROM institutions WHERE id = ?", institutionId);
            audit.record("INSTITUTION", institutionId, "ACCOUNT_DELETED", "INSTITUTION", institutionId, null);
        });
    }

    private static void rejectAdmin(AuthPrincipal principal) {
        if (principal.type() == PrincipalType.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_DELETABLE", "error.account.admin_not_deletable");
        }
    }

    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "error.account.not_found");
    }
}
