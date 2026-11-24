package com.raktaveda.common;

import java.util.Optional;
import java.util.regex.Pattern;

public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern INDIAN_LOCAL = Pattern.compile("^[6-9][0-9]{9}$");

    private PhoneNumbers() {
    }

    /**
     * Cleans a phone number into E.164 form (a plus sign, country code, digits, for
     * example +919876543210). A bare 10-digit Indian mobile number gets +91 added.
     * Returns empty when the number is not valid.
     * Needs review before real use: only Indian local numbers are auto-completed.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.replaceAll("[\\s\\-()]", "");
        if (INDIAN_LOCAL.matcher(s).matches()) {
            s = "+91" + s;
        }
        return E164.matcher(s).matches() ? Optional.of(s) : Optional.empty();
    }
}
