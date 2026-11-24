package com.raktaveda.auth;

/**
 * Sends a one-time code to an institution or admin email address.
 * "purpose" is one of the CodePurpose values, so the message can say what the code is for.
 * Implementations must NOT log the email address or the code.
 */
public interface EmailSender {

    void sendSecondStepCode(String email, String code, String purpose);
}
