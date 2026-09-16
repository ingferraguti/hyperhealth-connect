package io.hyperhealth.connect.controlplane.inventory.api;

import java.util.regex.Pattern;

import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;

/** Strong ETag representation for the endpoint logical row version. */
final class EndpointEtag {

    private static final Pattern CANONICAL_ETAG = Pattern.compile("\\\"rv-(0|[1-9][0-9]*)\\\"");

    private EndpointEtag() {}

    static String from(long rowVersion) {
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
        return "\"rv-" + rowVersion + "\"";
    }

    static long parseRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new MissingInventoryPreconditionException();
        }
        if (!CANONICAL_ETAG.matcher(value).matches()) {
            throw invalid();
        }
        try {
            long version = Long.parseLong(value.substring(4, value.length() - 1));
            return version;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static InvalidInventoryRequestException invalid() {
        return new InvalidInventoryRequestException("If-Match must contain one strong endpoint ETag");
    }
}
