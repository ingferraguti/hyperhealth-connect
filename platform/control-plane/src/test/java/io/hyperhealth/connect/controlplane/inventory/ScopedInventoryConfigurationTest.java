package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

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
