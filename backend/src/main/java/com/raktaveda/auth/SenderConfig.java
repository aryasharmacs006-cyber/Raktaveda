package com.raktaveda.auth;

import com.raktaveda.common.ApiException;
import com.raktaveda.config.RaktavedaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Chooses how codes are delivered.
 * - OTP_DEV_MODE=true: nothing is sent; the code is returned in the API response instead.
 * - Otherwise: delivery fails with 503 until you plug in a real sender below.
 *   To add one, write a class that implements SmsSender / EmailSender and return it here.
 */
@Configuration
public class SenderConfig {

    @Bean
    public SmsSender smsSender(RaktavedaProperties props) {
        if (props.otp().devMode()) {
            return (phone, code, purpose) -> {
                // Development only: deliberately does nothing and logs nothing.
            };
        }
        return (phone, code, purpose) -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_NOT_CONFIGURED", "error.sms.not_configured");
        };
    }

    @Bean
    public EmailSender emailSender(RaktavedaProperties props) {
        if (props.otp().devMode()) {
            return (email, code, purpose) -> {
                // Development only: deliberately does nothing and logs nothing.
            };
        }
        return (email, code, purpose) -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "error.email.not_configured");
        };
    }
}
