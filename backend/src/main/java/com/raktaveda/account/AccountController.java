package com.raktaveda.account;

import com.raktaveda.account.AccountDtos.DeleteConfirmDto;
import com.raktaveda.account.AccountDtos.DeleteStartDto;
import com.raktaveda.account.AccountDtos.DeleteStartResponse;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delete my own account. Open to users and to institutions in any status (even one still waiting for
 * verification may leave). Two confirmations are needed, see AccountDeletionService.
 */
@RestController
@RequestMapping("/api/account/delete")
@PreAuthorize("hasAnyRole('USER', 'INSTITUTION')")
public class AccountController {

    private final AccountDeletionService deletion;

    public AccountController(AccountDeletionService deletion) {
        this.deletion = deletion;
    }

    /** Confirmation 1. The body may be empty for a user. An institution sends {"password": "..."}. */
    @PostMapping("/start")
    public DeleteStartResponse start(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody(required = false) DeleteStartDto body) {
        return deletion.start(principal, body == null ? null : body.password());
    }

    /** Confirmation 2. Needs the code that was just sent and the exact word DELETE. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody DeleteConfirmDto body) {
        deletion.confirm(principal, body.challengeId(), body.code());
    }
}
