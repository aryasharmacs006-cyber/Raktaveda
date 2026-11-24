package com.raktaveda.auth;

/**
 * Tells a real SMS or email sender WHICH message to write, because "your login code" and
 * "your account deletion code" must not read the same.
 */
public final class CodePurpose {

    public static final String LOGIN = "LOGIN";
    public static final String ACCOUNT_DELETION = "ACCOUNT_DELETION";

    private CodePurpose() {
    }
}
