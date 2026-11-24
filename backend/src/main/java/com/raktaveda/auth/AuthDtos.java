package com.raktaveda.auth;

import com.raktaveda.institution.InstitutionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for /api/auth. Plain DTOs, never database rows. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record OtpRequestDto(@NotBlank @Size(max = 20) String phone) {
    }

    /** devCode is present ONLY when OTP_DEV_MODE=true. */
    public record OtpRequestResponse(UUID challengeId, int expiresInSeconds, String devCode) {
    }

    public record OtpVerifyDto(@NotNull UUID challengeId, @NotBlank @Pattern(regexp = "^[0-9]{4,10}$") String code) {
    }

    public record TokenResponse(String token, Instant expiresAt, String role) {
    }

    public record LoginDto(@NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(max = 72) String password) {
    }

    /** devCode is present ONLY when OTP_DEV_MODE=true. */
    public record StepOneResponse(UUID challengeId, int expiresInSeconds, String devCode) {
    }

    public record InstitutionRegisterDto(
            @NotBlank @Size(max = 200) String name,
            @NotNull InstitutionType type,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9/.\\- ]{3,100}$") String licenceNumber,
            @NotBlank @Email @Size(max = 254) String email,
            // Needs review before real use: password rule (12-72 chars, at least one letter and one digit).
            @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{12,72}$") String password,
            @NotBlank @Size(max = 20) String publicPhone,
            @NotBlank @Size(max = 20) String emergencyPhone,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Size(max = 300) String address) {
    }

    public record InstitutionRegisterResponse(UUID id, String status) {
    }

    public record MeResponse(UUID id, String role, String institutionStatus) {
    }
}
