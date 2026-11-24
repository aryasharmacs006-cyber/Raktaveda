package com.raktaveda.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void acceptsInternationalNumbers() {
        assertThat(PhoneNumbers.normalize("+1 555 555 0123")).contains("+15555550123");
    }

    @Test
    void addsIndiaCodeToTenDigitMobile() {
        assertThat(PhoneNumbers.normalize("98765 43210")).contains("+919876543210");
    }

    @Test
    void rejectsBadNumbers() {
        assertThat(PhoneNumbers.normalize(null)).isEmpty();
        assertThat(PhoneNumbers.normalize("12345")).isEmpty();
        assertThat(PhoneNumbers.normalize("+0123456789")).isEmpty();
        assertThat(PhoneNumbers.normalize("abcdefghij")).isEmpty();
        assertThat(PhoneNumbers.normalize("1234567890")).isEmpty(); // 10 digits but not an Indian mobile start
    }
}
