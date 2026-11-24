package com.raktaveda.auth;

/**
 * Sends a one-time code by SMS. Real providers cost money, so none is built in.
 * Implement this interface for the provider you choose and return it from SenderConfig.
 * "purpose" is one of the CodePurpose values, so the message can say what the code is for.
 * Implementations must NOT log the phone number or the code.
 */
public interface SmsSender {

    void sendOtp(String phoneE164, String code, String purpose);
}
