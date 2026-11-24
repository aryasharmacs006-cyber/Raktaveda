package com.raktaveda.auth;

import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.AuthDtos.OtpRequestResponse;
import com.raktaveda.auth.AuthDtos.TokenResponse;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.PhoneNumbers;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Login for USER accounts: phone number plus a one-time code sent by SMS. */
@Service
public class UserAuthService {

    /** Names the database column in the encryption, so the value cannot be moved to another column. */
    public static final String PHONE_CONTEXT = "users.phone";

    private final OtpService otp;
    private final CryptoService crypto;
    private final SmsSender sms;
    private final TokenService tokens;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final RaktavedaProperties props;

    public UserAuthService(OtpService otp, CryptoService crypto, SmsSender sms, TokenService tokens,
                           AuditService audit, JdbcTemplate jdbc, RaktavedaProperties props) {
        this.otp = otp;
        this.crypto = crypto;
        this.sms = sms;
        this.tokens = tokens;
        this.audit = audit;
        this.jdbc = jdbc;
        this.props = props;
    }

    /**
     * The answer is the same whether or not this phone already has an account, so nobody
     * can use this to find out who is registered.
     */
    public OtpRequestResponse requestOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "error.phone.invalid"));
        OtpService.Issued issued = otp.issueForPhone(crypto.blindIndex(phone), crypto.encrypt(phone, PHONE_CONTEXT));
        sms.sendOtp(phone, issued.code(), CodePurpose.LOGIN);
        audit.record("ANONYMOUS", null, "OTP_REQUESTED", "OTP_CHALLENGE", issued.challengeId(), "USER_LOGIN");
        return new OtpRequestResponse(issued.challengeId(), otp.ttlSeconds(),
                props.otp().devMode() ? issued.code() : null);
    }

    /** A correct code logs the user in. The account is created on first login. */
    public TokenResponse verifyOtp(UUID challengeId, String code) {
        OtpService.Verified verified = otp.verify(challengeId, code, OtpService.Purpose.USER_LOGIN, OtpService.Purpose.USER_LOGIN);
        UUID userId = jdbc.queryForObject("""
                INSERT INTO users (phone_index, phone_enc) VALUES (?, ?)
                ON CONFLICT (phone_index) DO UPDATE SET last_login_at = now()
                RETURNING id
                """, UUID.class, verified.phoneIndex(), verified.phoneEnc());
        TokenService.IssuedToken token = tokens.issue(PrincipalType.USER, userId);
        audit.record("USER", userId, "LOGIN_SUCCESS", "USER", userId, null);
        return new TokenResponse(token.token(), token.expiresAt(), PrincipalType.USER.name());
    }
}
