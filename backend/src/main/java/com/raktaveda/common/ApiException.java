package com.raktaveda.common;

import org.springframework.http.HttpStatus;

/**
 * An expected error. "code" is a short machine code (for example OTP_INVALID) and
 * "messageKey" is a translation key (for example error.otp.invalid). The frontend turns
 * the key into text in the user's language. The backend never sends display text.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String messageKey) {
        super(messageKey, null, false, false); // no stack trace: these are normal outcomes
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
