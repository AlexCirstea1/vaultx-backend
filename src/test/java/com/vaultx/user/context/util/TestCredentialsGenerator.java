package com.vaultx.user.context.util;

import lombok.Getter;

import java.util.UUID;

/**
 * Utility class for generating test credentials
 */
public class TestCredentialsGenerator {

    /**
     * Generates a unique test email address
     *
     * @param prefix optional prefix for the email (defaults to "test")
     * @return unique email address
     */
    public static String generateUniqueEmail(String prefix) {
        String emailPrefix = prefix != null ? prefix : "test";
        return emailPrefix + "+" + UUID.randomUUID() + "@example.com";
    }

    /**
     * Generates a unique test email address with default prefix
     *
     * @return unique email address
     */
    public static String generateUniqueEmail() {
        return generateUniqueEmail("test");
    }

    /**
     * Generates a unique username
     *
     * @param prefix optional prefix for the username (defaults to "User")
     * @return unique username
     */
    public static String generateUniqueUsername(String prefix) {
        String usernamePrefix = prefix != null ? prefix : "User";
        return usernamePrefix + System.currentTimeMillis();
    }

    /**
     * Generates a unique username with default prefix
     *
     * @return unique username
     */
    public static String generateUniqueUsername() {
        return generateUniqueUsername("User");
    }

    /**
     * Gets a standard test password
     *
     * @return test password
     */
    public static String getTestPassword() {
        return "P4ssw0rd!";
    }

    /**
     * Generates a complete set of test credentials
     *
     * @return test credentials object
     */
    public static TestCredentials generateTestCredentials() {
        return new TestCredentials(generateUniqueEmail(), generateUniqueUsername(), getTestPassword());
    }

    /**
     * Generates a complete set of test credentials with custom prefixes
     *
     * @param emailPrefix    prefix for the email
     * @param usernamePrefix prefix for the username
     * @return test credentials object
     */
    public static TestCredentials generateTestCredentials(String emailPrefix, String usernamePrefix) {
        return new TestCredentials(
                generateUniqueEmail(emailPrefix), generateUniqueUsername(usernamePrefix), getTestPassword());
    }

    /**
     * Convenience class to hold test user credentials
     */
    @Getter
    public static class TestCredentials {
        private final String email;
        private final String username;
        private final String password;

        public TestCredentials(String email, String username, String password) {
            this.email = email;
            this.username = username;
            this.password = password;
        }
    }
}
