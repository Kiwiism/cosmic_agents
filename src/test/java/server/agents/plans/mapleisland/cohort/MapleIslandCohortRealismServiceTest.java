package server.agents.plans.mapleisland.cohort;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentMapleIslandTravelRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleIslandCohortRealismServiceTest {
    @Test
    void fullPresetIsReplayableAndEnablesEveryControlledFeature() {
        AgentRuntimeEntry first = entry(41);
        AgentRuntimeEntry second = entry(41);

        long firstSeed = MapleIslandCohortRealismService.configure(
                first, MapleIslandCohortRealismMode.FULL, 991L, 3);
        long secondSeed = MapleIslandCohortRealismService.configure(
                second, MapleIslandCohortRealismMode.FULL, 991L, 3);

        assertEquals(firstSeed, secondSeed);
        assertEquals(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(first),
                AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(second));
        assertTrue(MapleIslandObjectiveRandomnessRuntime.settings(first).npcAnchorVariationEnabled());
        assertTrue(MapleIslandObjectiveRandomnessRuntime.settings(first).restSpotVariationEnabled());
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).routeVariationEnabled());
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).travelHopsEnabled());
        assertEquals(0.04d, AgentMapleIslandTravelRuntime.settings(first).travelHopProbability());
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).travelHopDecisionIntervalMs()
                >= 2_500L);
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).travelHopDecisionIntervalMs()
                <= 4_500L);
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).travelHopCooldownMs() >= 8_000L);
        assertTrue(AgentMapleIslandTravelRuntime.settings(first).travelHopCooldownMs() <= 15_000L);
    }

    @Test
    void lightKeepsSeededPacingAndRoutesButTurnsTravelHopsOff() {
        AgentRuntimeEntry entry = entry(42);

        MapleIslandCohortRealismService.configure(
                entry, MapleIslandCohortRealismMode.LIGHT, 992L, 1);

        assertTrue(MapleIslandObjectiveRandomnessRuntime.settings(entry).enabled());
        assertTrue(MapleIslandObjectiveRandomnessRuntime.settings(entry).npcAnchorVariationEnabled());
        assertTrue(MapleIslandObjectiveRandomnessRuntime.settings(entry).restSpotVariationEnabled());
        assertTrue(AgentMapleIslandTravelRuntime.settings(entry).routeVariationEnabled());
        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).travelHopsEnabled());
    }

    @Test
    void offIsAZeroDelayOptimalRouteControlGroup() {
        AgentRuntimeEntry entry = entry(43);

        MapleIslandCohortRealismService.configure(
                entry, MapleIslandCohortRealismMode.OFF, 993L, 1);

        assertEquals(0L, AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry));
        assertEquals(0L, AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry));
        assertFalse(MapleIslandObjectiveRandomnessRuntime.settings(entry).npcAnchorVariationEnabled());
        assertFalse(MapleIslandObjectiveRandomnessRuntime.settings(entry).restSpotVariationEnabled());
        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).routeVariationEnabled());
        assertFalse(AgentMapleIslandTravelRuntime.settings(entry).travelHopsEnabled());
    }

    @Test
    void identityAndOrdinalBothContributeToAgentSeed() {
        long first = MapleIslandCohortRealismService.agentSeed(entry(44), 123L, 1);
        long otherCharacter = MapleIslandCohortRealismService.agentSeed(entry(45), 123L, 1);
        long otherOrdinal = MapleIslandCohortRealismService.agentSeed(entry(44), 123L, 2);

        assertNotEquals(first, otherCharacter);
        assertNotEquals(first, otherOrdinal);
    }

    @Test
    void initialIntentionDelayIsReplayableAndBetweenTwoAndFiveSeconds() {
        long seed = MapleIslandCohortRealismService.agentSeed(entry(46), 124L, 1);

        long first = MapleIslandCohortRealismService.initialIntentionDelayMs(seed);
        long replay = MapleIslandCohortRealismService.initialIntentionDelayMs(seed);

        assertEquals(first, replay);
        assertTrue(first >= 2_000L);
        assertTrue(first <= 5_000L);
    }

    private static AgentRuntimeEntry entry(int characterId) {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(characterId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        return entry;
    }
}
