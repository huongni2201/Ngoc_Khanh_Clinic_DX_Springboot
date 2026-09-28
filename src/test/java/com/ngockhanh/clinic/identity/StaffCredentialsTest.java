package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.infrastructure.security.StaffPasswordEncoder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StaffCredentialsTest {
    @Test
    void verifiesEncodedPasswordsAndNeverAcceptsPlaintext() {
        var encoder = new StaffPasswordEncoder();
        String encoded = encoder.encode("correct-password");
        assertThat(encoded).startsWith("{bcrypt}$2");
        assertThat(encoder.matches("correct-password", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
        assertThat(encoder.matches("correct-password", "correct-password")).isFalse();
        assertThat(encoder.matches("correct-password", null)).isFalse();
    }

    @Test
    void rejectsUtf8PasswordLongerThanBcryptLimit() {
        var encoder = new StaffPasswordEncoder();
        assertThatThrownBy(() -> encoder.encode("é".repeat(37))).isInstanceOf(IllegalArgumentException.class);
    }
}
