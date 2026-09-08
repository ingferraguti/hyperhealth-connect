package io.hyperhealth.connect.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Defensive last-mile redaction for diagnostic text. Structured telemetry must still avoid
 * sensitive values at source; this class is not a substitute for data minimization.
 */
public final class SensitiveValueRedactor {

    private static final String MASK = "[REDACTED]";
    private static final List<Pattern> SENSITIVE_ASSIGNMENTS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+"),
            Pattern.compile("(?i)((?:patient|person|mrn|fiscalcode|name|birthdate|token)\\s*[:=]\\s*)[^,;\\s]+"));

    private SensitiveValueRedactor() {
    }

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (Pattern pattern : SENSITIVE_ASSIGNMENTS) {
            result = pattern.matcher(result).replaceAll("$1" + MASK);
        }
        return result;
    }
}

