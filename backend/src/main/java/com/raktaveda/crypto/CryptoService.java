package com.raktaveda.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encryption and keyed hashing, all from ONE master key in the environment variable
 * RAKTAVEDA_DATA_KEY (32 random bytes, base64). The key is never stored in code.
 *
 * Terms:
 * - AES-GCM: a cipher that hides the data AND detects tampering.
 * - HMAC: a hash that needs a secret key, so a stolen database alone cannot be used to
 *   guess values (for example all 6-digit codes or all phone numbers).
 * - Blind index: an HMAC of a value, stored next to the encrypted value so we can look
 *   a record up without ever storing the plain value.
 *
 * From the one master key we derive three separate keys (encryption, blind index, code
 * hashing), so a weakness in one use cannot be turned against another.
 *
 * Needs review before real use: key rotation and backups of the key.
 */
@Service
public class CryptoService {

    private static final byte FORMAT_VERSION = 1; // first byte of every ciphertext, for future key changes
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final byte[] indexKey;
    private final byte[] codeKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${raktaveda.data-key}") String base64Key) {
        byte[] master;
        try {
            master = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("RAKTAVEDA_DATA_KEY must be base64 text.");
        }
        if (master.length != 32) {
            throw new IllegalStateException("RAKTAVEDA_DATA_KEY must decode to exactly 32 bytes. "
                    + "Generate one with: openssl rand -base64 32");
        }
        this.aesKey = new SecretKeySpec(hmac(master, "aes-gcm/v1".getBytes(StandardCharsets.UTF_8)), "AES");
        this.indexKey = hmac(master, "blind-index/v1".getBytes(StandardCharsets.UTF_8));
        this.codeKey = hmac(master, "code-hash/v1".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encrypts text. "context" names the column (for example "users.phone"). It is mixed
     * into the check value, so a ciphertext copied into a different column will not decrypt.
     * Output is base64 of: version byte, 12-byte random IV, ciphertext+tag.
     */
    public String encrypt(String plain, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(1 + IV_BYTES + ct.length);
            buf.put(FORMAT_VERSION).put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed");
        }
    }

    public String decrypt(String encoded, String context) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length < 1 + IV_BYTES + TAG_BITS / 8 || all[0] != FORMAT_VERSION) {
                throw new IllegalStateException("Decryption failed");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, all, 1, IV_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(all, 1 + IV_BYTES, all.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Decryption failed"); // no data in the message
        }
    }

    /** Same input always gives the same output, so it can be searched and be unique. */
    public String blindIndex(String value) {
        return HexFormat.of().formatHex(hmac(indexKey, value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Hash of a one-time code. The challenge id is included so the same code gives a
     * different hash in every challenge. Plain SHA-256 would be unsafe here because there
     * are only a million 6-digit codes; the secret key makes guessing offline impossible.
     */
    public String codeHash(String challengeId, String code) {
        return HexFormat.of().formatHex(hmac(codeKey, (challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
    }

    /** Compares two texts in constant time, so timing does not leak how many characters matched. */
    public boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC is not available");
        }
    }
}
