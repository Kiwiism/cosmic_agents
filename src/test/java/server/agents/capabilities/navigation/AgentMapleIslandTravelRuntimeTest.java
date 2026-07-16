package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapleIslandTravelRuntimeTest {
    @Test
    void defaultsToCurrentBehaviorAndClearRestoresIt() {
        AgentRuntimeEntry entry = entry(17);

        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).routeVariationEnabled());
        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).travelHopsEnabled());

        AgentMapleIslandTravelRuntime.configure(entry, settings(91L, true, 1.2d, true, 1.0d, 1_000L, 3_000L));
        assertNotNull(AgentMapleIslandTravelRuntime.routeVariation(
                entry, 10000, 2, new Point(30, 40)));

        AgentMapleIslandTravelRuntime.clear(entry);
        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).routeVariationEnabled());
        assertFalse(AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(
                entry, 10_000L));
    }

    @Test
    void routeSeedAndHopRollAreDeterministicForSameAgentAndSettings() {
        AgentRuntimeEntry first = entry(23);
        AgentRuntimeEntry second = entry(23);
        AgentMapleIslandTravelSettings settings = settings(
                12345L, true, 1.2d, true, 0.5d, 1_000L, 3_000L);
        AgentMapleIslandTravelRuntime.configure(first, settings);
        AgentMapleIslandTravelRuntime.configure(second, settings);

        AgentMapleIslandTravelRuntime.RouteVariation firstVariation =
                AgentMapleIslandTravelRuntime.routeVariation(first, 1010100, 8, new Point(200, 300));
        AgentMapleIslandTravelRuntime.RouteVariation secondVariation =
                AgentMapleIslandTravelRuntime.routeVariation(second, 1010100, 8, new Point(200, 300));

        assertEquals(firstVariation, secondVariation);
        assertEquals(
                AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(
                        first, 1_000L),
                AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(
                        second, 1_000L));
    }

    @Test
    void decisionIntervalAndSuccessfulHopCooldownThrottleRolls() {
        AgentRuntimeEntry entry = entry(31);
        AgentMapleIslandTravelRuntime.configure(entry,
                settings(7L, false, 1.0d, true, 1.0d, 1_000L, 3_000L));
        assertTrue(AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(entry, 1_000L));
        AgentMapleIslandTravelRuntime.markTravelHopStarted(entry, 1_000L);
        assertFalse(AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(entry, 2_000L));
        assertFalse(AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(entry, 3_999L));
        assertTrue(AgentMapleIslandTravelRuntime.shouldAttemptTravelHop(entry, 4_000L));
    }

    private static AgentRuntimeEntry entry(int id) {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(id);
        return new AgentRuntimeEntry(bot, null, null);
    }

    private static AgentMapleIslandTravelSettings settings(long seed,
                                                            boolean varyRoute,
                                                            double stretch,
                                                            boolean hops,
                                                            double probability,
                                                            long decisionIntervalMs,
                                                            long cooldownMs) {
        return new AgentMapleIslandTravelSettings(
                seed, varyRoute, stretch, hops, probability, decisionIntervalMs, cooldownMs);
    }
}
