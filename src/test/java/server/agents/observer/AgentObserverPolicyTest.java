package server.agents.observer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentObserverPolicyTest {
    @Test
    void routesFromStationToTheNormalThreeMapCycle() {
        assertEquals(30_000,
                AgentObserverPolicy.nextHop(AgentObserverPolicy.STATION_MAP_ID,
                        AgentObserverPolicy.GREEN_SNAIL_MAP_ID));
        assertEquals(30_000,
                AgentObserverPolicy.nextHop(30_001, 40_000));
        assertEquals(40_000,
                AgentObserverPolicy.nextHop(30_000,
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

    @Test
    void routeActivationRemainsTrueAfterWatchedCharacterPassesStationAndNina() {
        assertFalse(AgentObserverPolicy.watchedReachedRoamingRoute(
                AgentObserverPolicy.MUSHROOM_TOWN_MAP_ID));
        assertTrue(AgentObserverPolicy.watchedReachedRoamingRoute(
                AgentObserverPolicy.STATION_MAP_ID));
        assertTrue(AgentObserverPolicy.watchedReachedRoamingRoute(
                AgentObserverPolicy.GREEN_SNAIL_MAP_ID));
        assertTrue(AgentObserverPolicy.watchedReachedRoamingRoute(
                AgentObserverPolicy.AMHERST_MAP_ID));
        assertTrue(AgentObserverPolicy.watchedReachedRoamingRoute(
                AgentObserverPolicy.MAI_MAP_ID));
        assertTrue(AgentObserverPolicy.watchedReachedRoamingRoute(1_010_100));
    }
}
