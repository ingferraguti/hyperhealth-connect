package io.hyperhealth.connect.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryAuditContextTest {

    @Test
    void acceptsCanonicalW3cVersionZeroTraceContext() {
        InventoryAuditContext context = InventoryAuditContext.create(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void replacesMalformedOrForbiddenTraceIdentifiers() {
        InventoryAuditContext malformed = InventoryAuditContext.create("00-NOT-VALID");
        InventoryAuditContext zero = InventoryAuditContext.create(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01");

        assertThat(malformed.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(zero.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(malformed.correlationId()).isNotEqualTo(zero.correlationId());
    }
}
