package server.agents.observer.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverActionProtocolTest {
    @Test
    void acceptsOnlyDefinedObserverActions() {
        assertTrue(ObserverActionProtocol.validAction(
                ObserverActionProtocol.ACTION_WARP_MAP));
        assertTrue(ObserverActionProtocol.validAction(
                ObserverActionProtocol.ACTION_WARP_CHARACTER));
        assertTrue(ObserverActionProtocol.validAction(
                ObserverActionProtocol.ACTION_REJOIN_TARGET));
        assertFalse(ObserverActionProtocol.validAction(0));
        assertFalse(ObserverActionProtocol.validAction(4));
    }
}
