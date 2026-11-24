package com.raktaveda.request;

import com.raktaveda.donor.BloodComponent;
import com.raktaveda.donor.BloodGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for emergency requests. Plain DTOs, never database rows. */
public final class RequestDtos {

    private RequestDtos() {
    }

    /**
     * A user must give latitude and longitude. A verified institution may leave both out
     * to use its own registered location.
     */
    public record CreateRequestDto(
            @NotNull BloodGroup bloodGroup,
            @NotNull BloodComponent component,
            @NotNull @Min(1) @Max(10) Integer units,
            @NotNull Urgency urgency,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    /** How many eligible, available donors with a compatible group are within each distance. */
    public record DonorCounts(int within5Km, int within10Km, int within25Km) {
    }

    /**
     * One donor who has accepted. contactMasked is always a hidden placeholder. phone is filled
     * only while the request is open and the donor has accepted (or donated).
     */
    public record ResponseView(UUID responseId, int donorNumber, BloodGroup bloodGroup, String status,
                               String contactMasked, String phone, Instant respondedAt) {
        @Override
        public String toString() {
            return "ResponseView[redacted]"; // never print a phone number by accident
        }
    }

    /** donorCounts and responses are filled only for a single request opened by its owner. */
    public record RequestView(UUID id, BloodGroup bloodGroup, BloodComponent component, int units,
                              Urgency urgency, String status, boolean needsTransport, Instant createdAt, Instant expiresAt,
                              DonorCounts donorCounts, List<ResponseView> responses) {
    }

    /** What a donor sees. Distance is rounded UP to whole kilometres. There is no location of any kind. */
    public record DonorRequestView(UUID id, BloodGroup bloodGroup, BloodComponent component, int units,
                                   Urgency urgency, boolean needsTransport, int distanceKm, Instant createdAt, Instant expiresAt,
                                   String myResponse) {
    }

    public record TransportDto(@NotNull Boolean needsTransport) {
    }

    public record DonorResponseResult(UUID requestId, String status) {
    }
}
