package io.hyperhealth.connect.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationEnvelopeTest {

    private static final IntegrationEnvelope.MessageId MESSAGE_ID =
            IntegrationEnvelope.MessageId.parse(
                    "urn:uuid:01991d95-0000-7000-8000-000000000001");
    private static final IntegrationEnvelope.MessageId CAUSATION_ID =
            IntegrationEnvelope.MessageId.parse(
                    "urn:uuid:01991d94-0000-7000-8000-000000000001");
    private static final IntegrationEnvelope.Scope SCOPE = new IntegrationEnvelope.Scope(
            "t-10000000-0000-4000-8000-000000000001",
            "f-30000000-0000-4000-8000-000000000001");
    private static final IntegrationEnvelope.Processing PROCESSING =
            new IntegrationEnvelope.Processing("flow-synthetic-lab-results", "1.0.0");
    private static final IntegrationEnvelope.Timing TIME =
            new IntegrationEnvelope.Timing(Instant.parse("2026-11-09T10:15:30.123456Z"));

    @Test
    void createsRootEnvelopeWithImmutableScopedIdentity() {
        var envelope = IntegrationEnvelope.root(
                MESSAGE_ID,
                new IntegrationEnvelope.CorrelationId("cor-HHC-SYNTHETIC-0001"),
                SCOPE,
                PROCESSING,
                TIME);

        assertEquals(IntegrationEnvelope.VERSION_1, envelope.envelopeVersion());
        assertEquals(MESSAGE_ID, envelope.messageId());
        assertEquals("t-10000000-0000-4000-8000-000000000001", envelope.scope().tenantId());
        assertEquals("f-30000000-0000-4000-8000-000000000001", envelope.scope().facilityId());
        assertEquals("flow-synthetic-lab-results", envelope.processing().flowId());
        assertEquals("1.0.0", envelope.processing().flowVersion());
        assertEquals(Instant.parse("2026-11-09T10:15:30.123456Z"), envelope.time().receivedAt());
        assertTrue(envelope.causationId().isEmpty());
    }

    @Test
    void preservesExplicitCausationWithoutConflatingItWithCorrelation() {
        var correlation = new IntegrationEnvelope.CorrelationId("cor-HHC-SYNTHETIC-episode-42");
        var envelope = IntegrationEnvelope.causedBy(
                MESSAGE_ID, correlation, CAUSATION_ID, SCOPE, PROCESSING, TIME);

        assertEquals(Optional.of(CAUSATION_ID), envelope.causationId());
        assertEquals(correlation, envelope.correlationId());
        assertFalse(envelope.correlationId().value().contains(CAUSATION_ID.wireValue()));
    }

    @Test
    void derivesAValidCorrelationTokenFromTheMessageId() {
        var correlation = IntegrationEnvelope.CorrelationId.from(MESSAGE_ID);

        assertEquals(MESSAGE_ID.wireValue(), correlation.value());
    }

    @Test
    void acceptsRfc9562UuidVersion4AndVersion7MessageIds() {
        var version4 = new IntegrationEnvelope.MessageId(
                UUID.fromString("3a357259-d9f5-4a84-993f-f3e78c3d4261"));

        assertEquals("urn:uuid:3a357259-d9f5-4a84-993f-f3e78c3d4261", version4.wireValue());
        assertEquals(7, MESSAGE_ID.value().version());
    }

    @Test
    void rejectsUnsupportedOrNonCanonicalMessageIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationEnvelope.MessageId.parse(
                        "01991d95-0000-7000-8000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationEnvelope.MessageId.parse(
                        "urn:uuid:01991D95-0000-7000-8000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.MessageId(
                        UUID.fromString("00000000-0000-0000-0000-000000000000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.MessageId(
                        UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210")));
    }

    @Test
    void rejectsUnsupportedEnvelopeVersionFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope(
                        "hhc-envelope/v2",
                        MESSAGE_ID,
                        new IntegrationEnvelope.CorrelationId("cor-HHC-SYNTHETIC-1"),
                        Optional.empty(),
                        SCOPE,
                        PROCESSING,
                        TIME));
    }

    @Test
    void rejectsSelfCausation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationEnvelope.causedBy(
                        MESSAGE_ID,
                        new IntegrationEnvelope.CorrelationId("cor-HHC-SYNTHETIC-1"),
                        MESSAGE_ID,
                        SCOPE,
                        PROCESSING,
                        TIME));
    }

    @Test
    void rejectsMissingOptionalContainerInsteadOfTreatingItAsAbsent() {
        assertThrows(
                NullPointerException.class,
                () -> new IntegrationEnvelope(
                        IntegrationEnvelope.VERSION_1,
                        MESSAGE_ID,
                        new IntegrationEnvelope.CorrelationId("cor-HHC-SYNTHETIC-1"),
                        null,
                        SCOPE,
                        PROCESSING,
                        TIME));
    }

    @Test
    void rejectsUnboundedOrUnsafeCorrelationValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.CorrelationId("cor synthetic with spaces"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.CorrelationId("cor\nforged-entry"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.CorrelationId("c".repeat(129)));
    }

    @Test
    void rejectsCrossTypeAndNonCanonicalScopeIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Scope(
                        "f-10000000-0000-4000-8000-000000000001",
                        "f-30000000-0000-4000-8000-000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Scope(
                        "t-10000000-0000-4000-8000-000000000001",
                        "f-30000000-0000-4000-8000-000000000001 "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Scope(
                        "t-00000000-0000-0000-0000-000000000000",
                        "f-30000000-0000-4000-8000-000000000001"));
    }

    @Test
    void acceptsCanonicalNonNilInventoryIdsSupportedByTheControlPlane() {
        var scope = new IntegrationEnvelope.Scope(
                "t-01991d95-0000-7000-8000-000000000010",
                "f-01991d95-0000-7000-8000-000000000011");

        assertEquals("t-01991d95-0000-7000-8000-000000000010", scope.tenantId());
        assertEquals("f-01991d95-0000-7000-8000-000000000011", scope.facilityId());
    }

    @Test
    void rejectsNonCanonicalOrMutableFlowReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Processing("FLOW-lab", "1.0.0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Processing("flow-lab results", "1.0.0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Processing("flow-lab", "latest"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Processing("flow-lab", "LaTeSt"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationEnvelope.Processing("flow-lab", "latest".repeat(30)));
    }

    @Test
    void rejectsMissingReceiveTime() {
        assertThrows(NullPointerException.class, () -> new IntegrationEnvelope.Timing(null));
    }
}
