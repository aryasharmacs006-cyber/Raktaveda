package com.raktaveda.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** password is required for an institution (it proves it is really them) and not used for a user. */
    public record DeleteStartDto(@Size(max = 72) String password) {
        @Override
        public String toString() {
            return "DeleteStartDto[redacted]";
        }
    }

    /** devCode is present ONLY when OTP_DEV_MODE=true. */
    public record DeleteStartResponse(UUID challengeId, int expiresInSeconds, String devCode) {
    }

    /** confirmText must be exactly DELETE. It is a second, deliberate action on top of the code. */
    public record DeleteConfirmDto(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "^[0-9]{4,10}$") String code,
            @NotBlank @Pattern(regexp = "^DELETE$") String confirmText) {
    }
}
