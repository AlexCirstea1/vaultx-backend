package ro.cloud.security.user.context.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class CipherUtils {

    public static String getHash(String input) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = hash.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
