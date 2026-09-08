package io.hyperhealth.connect.testkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Deterministic synthetic-only fixtures. Values use a reserved HHC test namespace. */
public final class SyntheticHl7 {

    private static final DateTimeFormatter HL7_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);

    private SyntheticHl7() {
    }

    public static String oruR01(String tenantCode, int sequence, LocalDateTime observedAt) {
        Objects.requireNonNull(tenantCode, "tenantCode");
        Objects.requireNonNull(observedAt, "observedAt");
        if (!tenantCode.matches("[A-Z0-9_]{2,16}") || sequence < 1) {
            throw new IllegalArgumentException("Invalid synthetic fixture parameters");
        }
        String controlId = "SYN_" + tenantCode + "_" + sequence;
        String time = observedAt.format(HL7_TIME);
        return "MSH|^~\\&|SYNTHETIC_LIS|" + tenantCode
                + "|HHC|TEST|" + time + "||ORU^R01|" + controlId + "|T|2.5.1\r"
                + "PID|1||SYNTHETIC_SUBJECT_" + sequence + "^^^HHC_TEST^PI\r"
                + "OBR|1|||HHC_TEST^Synthetic observation^99HHC|||" + time + "\r"
                + "OBX|1|NM|HHC_TEST_VALUE^Synthetic value^99HHC||42|1|||||F\r";
    }
}

