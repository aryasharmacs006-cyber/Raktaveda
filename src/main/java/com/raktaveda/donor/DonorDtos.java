package com.raktaveda.donor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/** Request and response shapes for /api/donor. Plain DTOs, never database rows. */
public final class DonorDtos {

    private DonorDtos() {
    }

    public record ProfileUpdateDto(
            @NotNull BloodGroup bloodGroup,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull @DecimalMin("20.0") @DecimalMax("300.0") Double weightKg,
            @PastOrPresent LocalDate lastDonationDate) {
    }

    /**
     * latitude and longitude are required when available is true and are ignored (and the
     * stored location erased) when it is false.
     */
    public record AvailabilityDto(
            @NotNull Boolean available,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    /** The donor's own data only. The stored location is never sent back, only whether one is set. */
    public record ProfileResponse(
            BloodGroup bloodGroup,
            LocalDate dateOfBirth,
            double weightKg,
            LocalDate lastDonationDate,
            boolean available,
            boolean locationSet,
            EligibilityService.Result eligibility) {
    }
}
