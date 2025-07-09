package com.vaultx.user.context.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class CipherUtils {

    public static String getHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Deprecated: remove or keep for backwards compatibility
    public static String getHash(String input) {
        return getHash(input.getBytes(/* choose correct charset if needed */));
    }

    public static String encryptPrivateKey(String privateKeyBase64) {
        try {
            // Server-side passphrase (ideally retrieved from a secure config or environment)
            String passphrase = "vaultx_2025_password";

            // Convert passphrase to key bytes (for illustration only, improve key derivation in production)
            byte[] keyBytes = Arrays.copyOf(passphrase.getBytes(StandardCharsets.UTF_8), 16); // 128-bit key
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Generate a random IV for AES
            byte[] iv = new byte[16];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Create and initialize cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

            // Encrypt the private key
            byte[] encryptedBytes = cipher.doFinal(privateKeyBase64.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            // Return as Base64 string
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting private key", e);
        }
    }
}
