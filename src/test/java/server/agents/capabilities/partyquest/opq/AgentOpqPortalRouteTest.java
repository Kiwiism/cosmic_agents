package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentOpqPortalRouteTest {
    @Test
    void learnsOnlyFromObservedTraversalOutcome() {
        AgentOpqPortalRoute route = new AgentOpqPortalRoute(2, 4);
        assertEquals(0, route.choice(0));
        route.observe(0, 0, false);
        assertEquals(1, route.choice(0));
        route.observe(0, 1, true);
        assertTrue(route.solved(0));
        assertEquals(1, route.choice(0));
        route.observe(0, 3, false);
        assertEquals(1, route.choice(0));
    }
}
