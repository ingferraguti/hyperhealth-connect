package io.hyperhealth.connect.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveValueRedactorTest {

    @Test
    void removesSyntheticIdentifiersAndBearerValues() {
        String diagnostic = "patient=SYNTHETIC_SUBJECT_001; mrn=SYNTHETIC_MRN_001; "
                + "Authorization: Bearer SYNTHETIC_ACCESS_TOKEN";

        String redacted = SensitiveValueRedactor.redact(diagnostic);

        assertFalse(redacted.contains("SYNTHETIC_SUBJECT_001"));
        assertFalse(redacted.contains("SYNTHETIC_MRN_001"));
        assertFalse(redacted.contains("SYNTHETIC_ACCESS_TOKEN"));
        assertTrue(redacted.contains("[REDACTED]"));
    }
}

