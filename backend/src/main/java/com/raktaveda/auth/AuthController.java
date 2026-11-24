package com.raktaveda.auth;

import com.raktaveda.audit.AuditService;
import com.raktaveda.auth.AuthDtos.InstitutionRegisterDto;
import com.raktaveda.auth.AuthDtos.InstitutionRegisterResponse;
import com.raktaveda.auth.AuthDtos.LoginDto;
import com.raktaveda.auth.AuthDtos.MeResponse;
import com.raktaveda.auth.AuthDtos.OtpRequestDto;
import com.raktaveda.auth.AuthDtos.OtpRequestResponse;
import com.raktaveda.auth.AuthDtos.OtpVerifyDto;
import com.raktaveda.auth.AuthDtos.StepOneResponse;
import com.raktaveda.auth.AuthDtos.TokenResponse;
import com.raktaveda.institution.InstitutionRegistrationService;
import com.raktaveda.security.AuthPrincipal;
import com.raktaveda.security.BearerTokenFilter;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthService users;
    private final TwoStepAuthService twoStep;
    private final InstitutionRegistrationService registration;
    private final TokenService tokens;
    private final AuditService audit;

    public AuthController(UserAuthService users, TwoStepAuthService twoStep,
                          InstitutionRegistrationService registration, TokenService tokens, AuditService audit) {
        this.users = users;
        this.twoStep = twoStep;
        this.registration = registration;
        this.tokens = tokens;
        this.audit = audit;
    }

    // ---- USER: phone + one-time code ----

    @PostMapping("/user/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestDto body) {
        return users.requestOtp(body.phone());
    }

    @PostMapping("/user/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyDto body) {
        return users.verifyOtp(body.challengeId(), body.code());
    }

    // ---- INSTITUTION: register, then email + password, then one-time code ----

    @PostMapping("/institution/register")
    @ResponseStatus(HttpStatus.CREATED)
    public InstitutionRegisterResponse register(@Valid @RequestBody InstitutionRegisterDto body) {
        return registration.register(body);
    }

    @PostMapping("/institution/login")
    public StepOneResponse institutionLogin(@Valid @RequestBody LoginDto body) {
        return twoStep.startLogin(PrincipalType.INSTITUTION, body.email(), body.password());
    }

    // ---- ADMIN: same two steps; the account itself is created only at startup ----

    @PostMapping("/admin/login")
    public StepOneResponse adminLogin(@Valid @RequestBody LoginDto body) {
        return twoStep.startLogin(PrincipalType.ADMIN, body.email(), body.password());
    }

    /** Step two for both institution and admin logins. */
    @PostMapping("/second-step/verify")
    public TokenResponse secondStepVerify(@Valid @RequestBody OtpVerifyDto body) {
        return twoStep.completeLogin(body.challengeId(), body.code());
    }

    // ---- Any logged-in account ----

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        String token = BearerTokenFilter.extractToken(request);
        if (token != null) {
            tokens.revoke(token);
        }
        audit.record(principal.type().name(), principal.id(), "LOGOUT", principal.type().name(), principal.id(), null);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return new MeResponse(principal.id(), principal.type().name(), principal.institutionStatus());
    }
}
