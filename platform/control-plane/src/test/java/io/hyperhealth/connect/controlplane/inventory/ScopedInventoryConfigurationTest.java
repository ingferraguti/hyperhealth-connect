package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class ScopedInventoryConfigurationTest {

    @Test
    void acceptsABoundedPoolConfiguration() {
        assertThatNoException().isThrownBy(() -> properties(20, 2, Duration.ofSeconds(2), Duration.ofSeconds(5))
                .validate());
    }

    @Test
    void rejectsUnboundedOrInternallyInconsistentPoolConfiguration() {
        assertThatThrownBy(() -> properties(257, 2, Duration.ofSeconds(2), Duration.ofSeconds(5))
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid platform database pool size");
        assertThatThrownBy(() -> properties(20, 2, Duration.ofSeconds(5), Duration.ofSeconds(5))
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("validationTimeout must be shorter than connectionTimeout");
        assertThatThrownBy(() -> properties(20, 2, Duration.ofMillis(249), Duration.ofSeconds(5))
                        .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("validationTimeout must be at least PT0.25S");
    }

    @Test
    void requiresASharedHighEntropyCursorKeyAndBoundedRetention() {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        ScopedInventoryConfiguration.InventoryApiProperties valid =
                new ScopedInventoryConfiguration.InventoryApiProperties(
                        key, Duration.ofMinutes(15), Duration.ofHours(24));
        assertThatNoException().isThrownBy(valid::validate);

        assertThatThrownBy(() -> new ScopedInventoryConfiguration.InventoryApiProperties(
                        "", Duration.ofMinutes(15), Duration.ofHours(24)).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cursorSigningKey");
        assertThatThrownBy(() -> new ScopedInventoryConfiguration.InventoryApiProperties(
                        key, Duration.ofHours(2), Duration.ofHours(24)).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cursorTtl");
    }

    @Test
    void requiresDistinctAuditKeysAndCanonicalSupplyChainDigests() {
        String integrityKey = encodedKey(0x11);
        String pseudonymizationKey = encodedKey(0x22);
        String digest = "sha256:" + "a".repeat(64);
        ScopedInventoryConfiguration.InventoryAuditProperties valid =
                new ScopedInventoryConfiguration.InventoryAuditProperties(
                        integrityKey, pseudonymizationKey, "audit-key-2026-01", digest, digest);

        assertThatNoException().isThrownBy(valid::validate);
        assertThatThrownBy(() -> new ScopedInventoryConfiguration.InventoryAuditProperties(
                        integrityKey, integrityKey, "audit-key-2026-01", digest, digest).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be distinct");
        assertThatThrownBy(() -> new ScopedInventoryConfiguration.InventoryAuditProperties(
                        integrityKey, pseudonymizationKey, "invalid key id", digest, digest).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("integrityKeyId");
        assertThatThrownBy(() -> new ScopedInventoryConfiguration.InventoryAuditProperties(
                        integrityKey, pseudonymizationKey, "audit-key-2026-01", "sha256:ABC", digest).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policyDigest");
    }

    private static String encodedKey(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ScopedInventoryConfiguration.PlatformDatabaseProperties properties(
            int maximumPoolSize,
            int minimumIdle,
            Duration validationTimeout,
            Duration connectionTimeout) {
        return new ScopedInventoryConfiguration.PlatformDatabaseProperties(
                "jdbc:postgresql://db.example.invalid:5432/hhc_control",
                "hhc_runtime",
                "HHC-SYNTHETIC-test-password",
                maximumPoolSize,
                minimumIdle,
                connectionTimeout,
                validationTimeout,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5));
    }
}
