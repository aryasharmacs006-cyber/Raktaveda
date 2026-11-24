package com.raktaveda.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All settings under "raktaveda." in application.yml, read once at startup.
 * The data encryption key is NOT here on purpose, so it can never be printed by accident.
 */
@ConfigurationProperties(prefix = "raktaveda")
public record RaktavedaProperties(Otp otp, Tokens tokens, Security security, RateLimit rateLimit, Admin admin,
                                  Eligibility eligibility, Donor donor, Requests requests) {

    /**
     * Rules for emergency requests. Needs review before real use: these numbers are policy choices.
     * maxAcceptedPerRequest limits how many donor phone numbers one request can collect.
     * retentionDays is how long finished requests are kept before they are deleted.
     */
    public record Requests(int expiryHours, int createPerHour, int maxOpenPerRequester,
                           int maxAcceptedPerRequest, int retentionDays) {
    }

    /**
     * Donor eligibility rules. Needs review before real use: verify every value against the
     * current NBTC and MoHFW guidelines before launch. These are an estimate only;
     * the blood bank makes the final decision (it also checks haemoglobin).
     */
    public record Eligibility(int minAgeYears, int maxAgeYears, double minWeightKg, int minDaysBetweenDonations) {
    }

    /** availabilityTtlHours: "Available now" switches itself off (and the location is erased) after this long. */
    public record Donor(int availabilityTtlHours) {
    }

    public record Otp(boolean devMode, int length, int ttlMinutes, int maxWrongAttempts,
                      int maxRequestsPerPhone, int requestWindowMinutes) {
    }

    public record Tokens(int userTtlHours, int institutionTtlHours, int adminTtlHours) {
    }

    public record Security(List<String> allowedOrigins, boolean requireHttps) {
    }

    public record RateLimit(int authPerMinute, int searchPerMinute, int generalPerMinute, int loginFailureLimit,
                            int chatPerMinute, int newConversationsPerHour, int reportsPerHour) {
    }

    public record Admin(String email, String password) {
        @Override
        public String toString() {
            return "Admin[redacted]"; // never print the admin password
        }
    }
}
