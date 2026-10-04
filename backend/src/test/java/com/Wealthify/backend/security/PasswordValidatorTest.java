package com.Wealthify.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordValidatorTest {

    @Test
    @DisplayName("Valid password accepted")
    void testValidPasswordAccepted() {
        String valid = "StrongPass123!";
        PasswordValidator.validate(valid);
        assertThat(PasswordValidator.isValid(valid)).isTrue();
    }

    @Test
    @DisplayName("Password of exactly 8 characters accepted")
    void testExactEightCharsAccepted() {
        String exactEight = "12345678";
        PasswordValidator.validate(exactEight);
        assertThat(PasswordValidator.isValid(exactEight)).isTrue();
    }

    @Test
    @DisplayName("Password of exactly 128 characters accepted")
    void testExact128CharsAccepted() {
        String exact128 = "A".repeat(128);
        PasswordValidator.validate(exact128);
        assertThat(PasswordValidator.isValid(exact128)).isTrue();
    }

    @Test
    @DisplayName("Empty password rejected")
    void testEmptyPasswordRejected() {
        assertThatThrownBy(() -> PasswordValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not be blank.");
        assertThat(PasswordValidator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Null password rejected")
    void testNullPasswordRejected() {
        assertThatThrownBy(() -> PasswordValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not be blank.");
        assertThat(PasswordValidator.isValid(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", "\t", "\n", " \t \n "})
    @DisplayName("Blank/whitespace-only password rejected")
    void testWhitespaceOnlyPasswordRejected(String blankPassword) {
        assertThatThrownBy(() -> PasswordValidator.validate(blankPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not be blank.");
        assertThat(PasswordValidator.isValid(blankPassword)).isFalse();
    }

    @Test
    @DisplayName("Password shorter than 8 characters rejected")
    void testShortPasswordRejected() {
        String shortPass = "1234567";
        assertThatThrownBy(() -> PasswordValidator.validate(shortPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be between 8 and 128 characters.");
        assertThat(PasswordValidator.isValid(shortPass)).isFalse();
    }

    @Test
    @DisplayName("Password longer than 128 characters rejected")
    void testLongPasswordRejected() {
        String longPass = "A".repeat(129);
        assertThatThrownBy(() -> PasswordValidator.validate(longPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be between 8 and 128 characters.");
        assertThat(PasswordValidator.isValid(longPass)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            " password123",
            "password123 ",
            " password123 ",
            "\tpassword123",
            "password123\t",
            "\npassword123",
            "password123\n"
    })
    @DisplayName("Password with leading or trailing accidental whitespace rejected")
    void testAccidentalWhitespaceRejected(String wsPassword) {
        assertThatThrownBy(() -> PasswordValidator.validate(wsPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not contain leading or trailing whitespace.");
        assertThat(PasswordValidator.isValid(wsPassword)).isFalse();
    }

    @Test
    @DisplayName("Passphrases with internal whitespace accepted")
    void testInternalWhitespaceAllowed() {
        String passphrase = "correct horse battery staple";
        PasswordValidator.validate(passphrase);
        assertThat(PasswordValidator.isValid(passphrase)).isTrue();
    }

    @Test
    @DisplayName("Validation exception messages never expose the sensitive password value")
    void testNoPasswordLeakageInExceptionMessage() {
        String sensitiveAttempt = "secret1";
        try {
            PasswordValidator.validate(sensitiveAttempt);
        } catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage()).doesNotContain(sensitiveAttempt);
            assertThat(ex.getMessage()).contains("Password must be between 8 and 128 characters.");
        }
    }
}
