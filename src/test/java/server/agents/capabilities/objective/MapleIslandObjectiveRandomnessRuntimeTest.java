package server.agents.capabilities.objective;

import org.junit.jupiter.api.Test;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandObjectiveRandomnessRuntimeTest {
    @Test
    void defaultsOffAndLeavesExistingProfileRangesInControl() {
        AgentRuntimeEntry entry = entryWithProfile();

        assertFalse(MapleIslandObjectiveRandomnessRuntime.settings(entry).enabled());
        assertEquals(OptionalInt.empty(),
                MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                        entry, 10000, 2101, 5));
        for (int sample = 0; sample < 20; sample++) {
            assertTrue(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry) >= 600L);
            assertTrue(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry) <= 1_400L);
            assertTrue(AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry) >= 900L);
            assertTrue(AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry) <= 1_800L);
        }
    }

    @Test
    void sameSeedReplaysDelayAndAnchorDecisionStreams() {
        AgentRuntimeEntry first = entryWithProfile();
        AgentRuntimeEntry second = entryWithProfile();
        MapleIslandObjectiveRandomnessSettings settings =
                MapleIslandObjectiveRandomnessSettings.cohort(42_4242L);
        MapleIslandObjectiveRandomnessRuntime.configure(first, settings);
        MapleIslandObjectiveRandomnessRuntime.configure(second, settings);

        for (int decision = 0; decision < 20; decision++) {
            assertEquals(AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(first),
                    AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(second));
            assertEquals(AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(first),
                    AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(second));
            assertEquals(MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                            first, 1010000, 20100, 6),
                    MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                            second, 1010000, 20100, 6));
            assertEquals(MapleIslandObjectiveRandomnessRuntime.selectRestSpotIndex(
                            first, 2000000, 20),
                    MapleIslandObjectiveRandomnessRuntime.selectRestSpotIndex(
                            second, 2000000, 20));
            assertEquals(MapleIslandObjectiveRandomnessRuntime.selectRestFacingDirection(
                            first, 2000000),
                    MapleIslandObjectiveRandomnessRuntime.selectRestFacingDirection(
                            second, 2000000));
        }
    }

    @Test
    void cohortSeedsCoverAllSouthperryPostPlanBehaviors() {
        EnumSet<AgentPlanCompletionMode> behaviors =
                EnumSet.noneOf(AgentPlanCompletionMode.class);
        AgentRuntimeEntry entry = entryWithProfile();
        for (long seed = 0L; seed < 500L; seed++) {
            MapleIslandObjectiveRandomnessRuntime.configure(
                    entry, MapleIslandObjectiveRandomnessSettings.cohort(seed));
            behaviors.add(MapleIslandObjectiveRandomnessRuntime.selectPostPlanBehavior(
                    entry, 2000000));
        }

        assertEquals(EnumSet.allOf(AgentPlanCompletionMode.class), behaviors);
    }

    @Test
    void optionalOverridesAreConfigurableAndAnchorToggleIsIndependent() {
        AgentRuntimeEntry entry = entryWithProfile();
        MapleIslandObjectiveRandomnessRuntime.configure(entry,
                new MapleIslandObjectiveRandomnessSettings(
                        true,
                        7L,
                        new AgentBehaviorProfile.DelayRange(2_500, 2_500),
                        new AgentBehaviorProfile.DelayRange(4_000, 4_000),
                        false,
                        false,
                        false));

        assertEquals(2_500L, AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry));
        assertEquals(4_000L, AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry));
        assertTrue(MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                entry, 10000, 2101, 5).isEmpty());
        assertEquals(0, MapleIslandObjectiveRandomnessRuntime.selectRestSpotIndex(
                entry, 2000000, 20).orElseThrow());
        assertEquals(1, MapleIslandObjectiveRandomnessRuntime.selectRestFacingDirection(
                entry, 2000000).orElseThrow());

        MapleIslandObjectiveRandomnessRuntime.configure(
                entry, MapleIslandObjectiveRandomnessSettings.cohort(7L));
        assertTrue(MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                entry, 999999999, 9999999, 0).isEmpty());

        MapleIslandObjectiveRandomnessRuntime.clear(entry);
        assertFalse(MapleIslandObjectiveRandomnessRuntime.settings(entry).enabled());
    }

    @Test
    void enabledNullOverridesUseProfileRangesWithSeededSampling() {
        AgentRuntimeEntry first = entryWithProfile();
        AgentRuntimeEntry second = entryWithProfile();
        MapleIslandObjectiveRandomnessSettings settings =
                new MapleIslandObjectiveRandomnessSettings(
                        true, 99L, null, null, true, true, true);
        MapleIslandObjectiveRandomnessRuntime.configure(first, settings);
        MapleIslandObjectiveRandomnessRuntime.configure(second, settings);

        for (int decision = 0; decision < 10; decision++) {
            long firstNpc = AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(first);
            long secondNpc = AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(second);
            assertEquals(firstNpc, secondNpc);
            assertTrue(firstNpc >= 600L && firstNpc <= 1_400L);
        }
    }

    @Test
    void cashShopVisitTimingIsSeededAndReplayableForControlledRuns() {
        AgentRuntimeEntry first = entryWithProfile();
        AgentRuntimeEntry replay = entryWithProfile();
        AgentRuntimeEntry other = entryWithProfile();
        MapleIslandObjectiveRandomnessRuntime.configure(
                first, MapleIslandObjectiveRandomnessSettings.cohort(123L));
        MapleIslandObjectiveRandomnessRuntime.configure(
                replay, MapleIslandObjectiveRandomnessSettings.cohort(123L));
        MapleIslandObjectiveRandomnessRuntime.configure(
                other, MapleIslandObjectiveRandomnessSettings.cohort(456L));

        List<Long> firstSamples = new ArrayList<>();
        List<Long> replaySamples = new ArrayList<>();
        List<Long> otherSamples = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            firstSamples.add(MapleIslandObjectiveRandomnessRuntime
                    .sampleCashShopVisitDelayMs(first, 2_500L, 5_000L).orElseThrow());
            replaySamples.add(MapleIslandObjectiveRandomnessRuntime
                    .sampleCashShopVisitDelayMs(replay, 2_500L, 5_000L).orElseThrow());
            otherSamples.add(MapleIslandObjectiveRandomnessRuntime
                    .sampleCashShopVisitDelayMs(other, 2_500L, 5_000L).orElseThrow());
        }

        assertEquals(firstSamples, replaySamples);
        assertNotEquals(firstSamples, otherSamples);
        assertTrue(firstSamples.stream().allMatch(delay -> delay >= 2_500L && delay <= 5_000L));
    }

    private static AgentRuntimeEntry entryWithProfile() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        return entry;
    }
}
