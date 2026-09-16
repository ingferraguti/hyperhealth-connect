package io.hyperhealth.connect.controlplane.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.hyperhealth.connect.controlplane.inventory.InvalidInventoryRequestException;

class EndpointEtagTest {

    @Test
    void roundTripsOnlyOneStrongVersionTag() {
        assertThat(EndpointEtag.from(42)).isEqualTo("\"rv-42\"");
        assertThat(EndpointEtag.parseRequired("\"rv-42\"")).isEqualTo(42);
        assertThat(EndpointEtag.parseRequired("\"rv-0\"")).isZero();

        assertThatThrownBy(() -> EndpointEtag.parseRequired("W/\"rv-42\""))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired("*"))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired("\"rv-42\", \"rv-43\""))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired("\"rv-01\""))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired("\"rv-+1\""))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired("\"rv--0\""))
                .isInstanceOf(InvalidInventoryRequestException.class);
        assertThatThrownBy(() -> EndpointEtag.parseRequired(null))
                .isInstanceOf(MissingInventoryPreconditionException.class);
    }
}
