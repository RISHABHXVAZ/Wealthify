package com.Wealthify.backend.security;

/**
 * Validates password constraints across registration and password-reset flows.
 *
 * Enforces:
 * - Minimum length: 8 characters
 * - Maximum length: 128 characters
 * - Non-blank (cannot be empty or whitespace-only)
 * - No leading or trailing accidental whitespace
 * - Safe sanitization: Never logs, stores, or includes raw password values in exceptions.
 */
public final class PasswordValidator {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordValidator() {}

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank.");
        }

        if (Character.isWhitespace(password.charAt(0)) || Character.isWhitespace(password.charAt(password.length() - 1))) {
            throw new IllegalArgumentException("Password must not contain leading or trailing whitespace.");
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters.");
        }
    }

    public static boolean isValid(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        if (Character.isWhitespace(password.charAt(0)) || Character.isWhitespace(password.charAt(password.length() - 1))) {
            return false;
        }
        return password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
    }
}
