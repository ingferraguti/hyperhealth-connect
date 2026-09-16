package io.hyperhealth.connect.controlplane.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.hyperhealth.connect.controlplane.inventory.EndpointQuery;
import io.hyperhealth.connect.controlplane.inventory.InventoryActor;
import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;
import io.hyperhealth.connect.controlplane.inventory.VerifiedFacilityScope;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.EndpointId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.FacilityId;
import io.hyperhealth.connect.controlplane.inventory.InventoryId.TenantId;

class InventoryCursorCodecTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final byte[] SIGNING_KEY = new byte[32];

    private final InventoryActor actor = new InventoryActor("subject-1", "control-plane-ui");
    private final VerifiedFacilityScope scope = new VerifiedFacilityScope(
            new TenantId(UUID.randomUUID()), new FacilityId(UUID.randomUUID()));
    private final EndpointQuery query = new EndpointQuery(
            Optional.empty(), Optional.empty(), Optional.empty(), 50);

    @Test
    void roundTripsAnAuthenticatedShortLivedCursor() {
        EndpointId last = EndpointId.newId();
        InventoryCursorCodec codec = codecAt(NOW);

        String encoded = codec.encode(actor, scope, query, last);

        assertThat(codec.decode(encoded, actor, scope, query)).contains(last);
        assertThat(encoded).doesNotContain(actor.subject());
        assertThat(encoded).doesNotContain(scope.tenantId().externalForm());
    }

    @Test
    void rejectsTamperingAndContextSubstitution() {
        InventoryCursorCodec codec = codecAt(NOW);
        String encoded = codec.encode(actor, scope, query, EndpointId.newId());
        String tampered = (encoded.charAt(0) == 'A' ? "B" : "A") + encoded.substring(1);
        VerifiedFacilityScope sibling = new VerifiedFacilityScope(
                scope.tenantId(), new FacilityId(UUID.randomUUID()));

        assertThatThrownBy(() -> codec.decode(tampered, actor, scope, query))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> codec.decode(encoded, actor, sibling, query))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> codec.decode(
                        encoded,
                        new InventoryActor("another-subject", actor.authorizedParty()),
                        scope,
                        query))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> codec.decode(
                        encoded,
                        actor,
                        scope,
                        new EndpointQuery(Optional.empty(), Optional.empty(), Optional.empty(), 10)))
                .isInstanceOf(InvalidInventoryRequestException.class);
    }

    @Test
    void rejectsAnExpiredCursor() {
        String encoded = codecAt(NOW).encode(actor, scope, query, EndpointId.newId());

        assertThatThrownBy(() -> codecAt(NOW.plus(Duration.ofMinutes(16)))
                        .decode(encoded, actor, scope, query))
                .isInstanceOf(InvalidInventoryRequestException.class);
    }

    private static InventoryCursorCodec codecAt(Instant instant) {
        return new InventoryCursorCodec(
                SIGNING_KEY,
                Duration.ofMinutes(15),
                Clock.fixed(instant, ZoneOffset.UTC));
    }
}
