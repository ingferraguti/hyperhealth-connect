package io.hyperhealth.connect.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SyntheticHl7Test {

    @Test
    void emitsClearlyMarkedDeterministicTestData() {
        String message = SyntheticHl7.oruR01("TENANT_A", 1, LocalDateTime.of(2026, 9, 7, 12, 0));

        assertTrue(message.contains("SYNTHETIC_SUBJECT_1"));
        assertTrue(message.contains("|T|2.5.1"));
        assertFalse(message.contains("|P|"));
    }
}

