package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed, immutable identifier for a control-plane inventory resource.
 *
 * <p>The type prefix is part of the canonical external representation. It prevents an identifier
 * for one resource kind from being silently accepted as another kind at an API or persistence
 * boundary. UUID values are never derived from display names or healthcare identifiers.</p>
 */
public sealed interface InventoryId
        permits InventoryId.TenantId,
                InventoryId.OrganizationId,
                InventoryId.FacilityId,
                InventoryId.ApplicationId,
                InventoryId.EndpointId,
                InventoryId.RuntimeCellId {

    UUID value();

    String prefix();

    default String externalForm() {
        return prefix() + value();
    }

    record TenantId(UUID value) implements InventoryId {
        private static final String PREFIX = "t-";

        public TenantId {
            value = requireUsable(value);
        }

        public static TenantId newId() {
            return new TenantId(UUID.randomUUID());
        }

        public static TenantId parse(String externalForm) {
            return new TenantId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    record OrganizationId(UUID value) implements InventoryId {
        private static final String PREFIX = "o-";

        public OrganizationId {
            value = requireUsable(value);
        }

        public static OrganizationId newId() {
            return new OrganizationId(UUID.randomUUID());
        }

        public static OrganizationId parse(String externalForm) {
            return new OrganizationId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    record FacilityId(UUID value) implements InventoryId {
        private static final String PREFIX = "f-";

        public FacilityId {
            value = requireUsable(value);
        }

        public static FacilityId newId() {
            return new FacilityId(UUID.randomUUID());
        }

        public static FacilityId parse(String externalForm) {
            return new FacilityId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    record ApplicationId(UUID value) implements InventoryId {
        private static final String PREFIX = "a-";

        public ApplicationId {
            value = requireUsable(value);
        }

        public static ApplicationId newId() {
            return new ApplicationId(UUID.randomUUID());
        }

        public static ApplicationId parse(String externalForm) {
            return new ApplicationId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    record EndpointId(UUID value) implements InventoryId {
        private static final String PREFIX = "ep-";

        public EndpointId {
            value = requireUsable(value);
        }

        public static EndpointId newId() {
            return new EndpointId(UUID.randomUUID());
        }

        public static EndpointId parse(String externalForm) {
            return new EndpointId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    record RuntimeCellId(UUID value) implements InventoryId {
        private static final String PREFIX = "rc-";

        public RuntimeCellId {
            value = requireUsable(value);
        }

        public static RuntimeCellId newId() {
            return new RuntimeCellId(UUID.randomUUID());
        }

        public static RuntimeCellId parse(String externalForm) {
            return new RuntimeCellId(parseValue(externalForm, PREFIX));
        }

        @Override
        public String prefix() {
            return PREFIX;
        }

        @Override
        public String toString() {
            return externalForm();
        }
    }

    private static UUID requireUsable(UUID value) {
        Objects.requireNonNull(value, "value");
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("The nil UUID is reserved and cannot identify a resource");
        }
        return value;
    }

    private static UUID parseValue(String externalForm, String expectedPrefix) {
        Objects.requireNonNull(externalForm, "externalForm");
        if (!externalForm.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Identifier must start with " + expectedPrefix);
        }
        String rawValue = externalForm.substring(expectedPrefix.length());
        try {
            UUID parsed = UUID.fromString(rawValue);
            if (!parsed.toString().equals(rawValue)) {
                throw new IllegalArgumentException("Identifier is not in canonical UUID form");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Identifier is not in canonical UUID form", exception);
        }
    }
}
