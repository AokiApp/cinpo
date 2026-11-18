package app.aoki.cinpo.runtime.pcsc;

import app.aoki.cinpo.runtime.ApduChannelFactory;
import app.aoki.cinpo.runtime.RuntimeAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a runtime module contributes exactly its own adapter to discovery.
 */
class AdapterDiscoveryTest {

    @Test
    void onlyTheDeclaredRuntimeIsDiscoverable() {
        List<RuntimeAdapter> adapters = ApduChannelFactory.registered();
        assertEquals(1, adapters.size());
        assertEquals("pcsc", adapters.get(0).name());
    }
}
