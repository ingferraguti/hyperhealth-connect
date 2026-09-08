package io.hyperhealth.connect.sdk;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectorDescriptorTest {

    @Test
    void rejectsBlankStableIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectorDescriptor(" ", "0.1.0", "[0.1,1.0)", Set.of("ingress")));
    }
}

