package io.hyperhealth.connect.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationEnvelopeTest {

    @Test
    void preservesExplicitTenantAndFacilityScope() {
        var scope = new IntegrationEnvelope.Scope("tenant-a", "organization-a", "facility-a");
        var envelope = new IntegrationEnvelope(
                UUID.fromString("3a357259-d9f5-4a84-993f-f3e78c3d4261"),
                UUID.fromString("57c77c39-4682-4f17-b152-c427c36e4214"),
                scope,
                Instant.parse("2026-09-07T00:00:00Z"),
                "synthetic-lis",
                "raw://tenant-a/event-1",
                "0".repeat(64),
                "hl7v2/oru-r01@1");

        assertEquals("tenant-a", envelope.scope().tenantId());
        assertEquals("facility-a", envelope.scope().facilityId());
    }

    @Test
    void rejectsInvalidPayloadDigest() {
        var scope = new IntegrationEnvelope.Scope("tenant-a", "organization-a", "facility-a");
        assertThrows(IllegalArgumentException.class, () -> new IntegrationEnvelope(
                UUID.randomUUID(), UUID.randomUUID(), scope, Instant.EPOCH,
                "synthetic-lis", "raw://event", "not-a-digest", "contract@1"));
    }
}

