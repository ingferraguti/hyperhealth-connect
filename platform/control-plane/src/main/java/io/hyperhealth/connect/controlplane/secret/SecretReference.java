package io.hyperhealth.connect.controlplane.secret;

import java.util.Objects;
import java.util.UUID;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

/**
 * Scoped metadata for an external secret binding.
 *
 * <p>The backend binding identifier is an opaque UUID resolved by deployment-local configuration.
 * No provider path, version, credential, key, token, certificate or encrypted value can be held by
 * this type.</p>
 */
public final class SecretReference {

    private final SecretReferenceId id;
    private final TenantId tenantId;
    private final FacilityId facilityId;
    private final SecretProvider provider;
    private final UUID backendBindingId;
    private final SecretPurpose purpose;
    private final SecretReferenceState state;
    private final long rowVersion;

    public SecretReference(
            SecretReferenceId id,
            TenantId tenantId,
            FacilityId facilityId,
            SecretProvider provider,
            UUID backendBindingId,
            SecretPurpose purpose,
            SecretReferenceState state,
            long rowVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.facilityId = Objects.requireNonNull(facilityId, "facilityId");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.backendBindingId = requireUsableBindingId(backendBindingId);
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.state = Objects.requireNonNull(state, "state");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
        this.rowVersion = rowVersion;
    }

    public static SecretReference active(
            SecretReferenceId id,
            TenantId tenantId,
            FacilityId facilityId,
            SecretProvider provider,
            UUID backendBindingId,
            SecretPurpose purpose) {
        return new SecretReference(
                id, tenantId, facilityId, provider, backendBindingId, purpose, SecretReferenceState.ACTIVE, 0);
    }

    public SecretReferenceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public FacilityId facilityId() {
        return facilityId;
    }

    public SecretProvider provider() {
        return provider;
    }

    UUID backendBindingId() {
        return backendBindingId;
    }

    public SecretPurpose purpose() {
        return purpose;
    }

    public SecretReferenceState state() {
        return state;
    }

    public long rowVersion() {
        return rowVersion;
    }

    public SecretReferenceExport toExport() {
        return SecretReferenceExport.from(this);
    }

    @Override
    public String toString() {
        return "SecretReference[id=" + id.externalForm()
                + ", provider=" + provider
                + ", purpose=" + purpose
                + ", state=" + state
                + ", backendBindingId=[REDACTED]]";
    }

    private static UUID requireUsableBindingId(UUID value) {
        Objects.requireNonNull(value, "backendBindingId");
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("The nil UUID cannot identify a backend binding");
        }
        return value;
    }
}
