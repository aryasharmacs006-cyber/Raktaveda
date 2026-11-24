package com.raktaveda.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** The phone's push token, as given by the Firebase library in the app. */
    public record DeviceTokenDto(
            @NotBlank @Size(min = 20, max = 4096) @Pattern(regexp = "^[A-Za-z0-9_:.\\-]+$") String token) {
        @Override
        public String toString() {
            return "DeviceTokenDto[redacted]";
        }
    }
}
