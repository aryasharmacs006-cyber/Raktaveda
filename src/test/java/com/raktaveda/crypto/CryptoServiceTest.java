package com.raktaveda.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static String newKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final CryptoService crypto = new CryptoService(newKey());

    @Test
    void encryptThenDecryptGivesBackTheText() {
        String enc = crypto.encrypt("+15555550123", "users.phone");
        assertThat(enc).doesNotContain("5555550123");
        assertThat(crypto.decrypt(enc, "users.phone")).isEqualTo("+15555550123");
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        assertThat(crypto.encrypt("hello", "x")).isNotEqualTo(crypto.encrypt("hello", "x"));
    }

    @Test
    void wrongContextFailsToDecrypt() {
        String enc = crypto.encrypt("hello", "users.phone");
        assertThatThrownBy(() -> crypto.decrypt(enc, "institutions.public_phone"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        byte[] bytes = Base64.getDecoder().decode(crypto.encrypt("hello", "x"));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> crypto.decrypt(tampered, "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String enc = crypto.encrypt("hello", "x");
        CryptoService other = new CryptoService(newKey());
        assertThatThrownBy(() -> other.decrypt(enc, "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blindIndexIsStableAndKeyed() {
        assertThat(crypto.blindIndex("+15555550123")).isEqualTo(crypto.blindIndex("+15555550123"));
        assertThat(crypto.blindIndex("+15555550123")).isNotEqualTo(crypto.blindIndex("+15555550124"));
        assertThat(new CryptoService(newKey()).blindIndex("+15555550123")).isNotEqualTo(crypto.blindIndex("+15555550123"));
    }

    @Test
    void codeHashDependsOnChallengeAndCode() {
        assertThat(crypto.codeHash("a", "123456")).isEqualTo(crypto.codeHash("a", "123456"));
        assertThat(crypto.codeHash("a", "123456")).isNotEqualTo(crypto.codeHash("b", "123456"));
        assertThat(crypto.codeHash("a", "123456")).isNotEqualTo(crypto.codeHash("a", "123457"));
    }

    @Test
    void badKeysAreRejected() {
        assertThatThrownBy(() -> new CryptoService("not base64!!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CryptoService(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
