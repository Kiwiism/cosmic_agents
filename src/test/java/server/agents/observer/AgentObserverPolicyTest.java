package server.agents.observer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentObserverPolicyTest {
    @Test
    void routesFromStationToTheNormalThreeMapCycle() {
        assertEquals(30_000,
                AgentObserverPolicy.nextHop(AgentObserverPolicy.STATION_MAP_ID,
                        AgentObserverPolicy.GREEN_SNAIL_MAP_ID));
        assertEquals(AgentObserverPolicy.AMHERST_MAP_ID,
                AgentObserverPolicy.nextHop(AgentObserverPolicy.GREEN_SNAIL_MAP_ID,
                        AgentObserverPolicy.MAI_MAP_ID));
        assertEquals(AgentObserverPolicy.AMHERST_MAP_ID,
                AgentObserverPolicy.nextHop(AgentObserverPolicy.MAI_MAP_ID,
                        AgentObserverPolicy.GREEN_SNAIL_MAP_ID));
    }

    @Test
    void routesNormallyFromMaiToSouthperry() {
        assertEquals(1_020_000,
                AgentObserverPolicy.nextHop(AgentObserverPolicy.MAI_MAP_ID,
                        AgentObserverPolicy.SOUTHPERRY_MAP_ID));
    }

    @Test
    void isolatedTrainingMapsRequireTheExplicitWarpPolicy() {
        for (int mapId : AgentObserverPolicy.ISOLATED_TRAINING_MAPS) {
            assertTrue(AgentObserverPolicy.ISOLATED_TRAINING_MAP_SET.contains(mapId));
            assertNull(AgentObserverPolicy.nextHop(AgentObserverPolicy.MAI_MAP_ID, mapId));
        }
    }
}
