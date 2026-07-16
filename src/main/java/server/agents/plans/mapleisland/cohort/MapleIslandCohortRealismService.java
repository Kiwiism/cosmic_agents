package server.agents.plans.mapleisland.cohort;

import server.agents.capabilities.navigation.AgentMapleIslandTravelRuntime;
import server.agents.capabilities.navigation.AgentMapleIslandTravelSettings;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.runtime.AgentRuntimeEntry;

/** Applies one deterministic, run-scoped realism preset to a cohort Agent. */
public final class MapleIslandCohortRealismService {
    private static final double LIGHT_MAX_ROUTE_STRETCH = 1.08d;
    private static final double FULL_MAX_ROUTE_STRETCH = 1.15d;
    private static final double FULL_TRAVEL_HOP_PROBABILITY = 0.04d;
    private static final AgentBehaviorProfile.DelayRange NO_DELAY =
            new AgentBehaviorProfile.DelayRange(0, 0);
    private static final long HOP_INTERVAL_DOMAIN = 0x484F502D494E5456L;
    private static final long HOP_COOLDOWN_DOMAIN = 0x484F502D434F4F4CL;

    private MapleIslandCohortRealismService() {
    }

    public static long configure(AgentRuntimeEntry entry,
                                 MapleIslandCohortRealismMode mode,
                                 long runSeed,
                                 int ordinal) {
        if (entry == null || ordinal <= 0) {
            throw new IllegalArgumentException("cohort Agent entry and positive ordinal are required");
        }
        MapleIslandCohortRealismMode selected = mode == null
                ? MapleIslandCohortRealismMode.LIGHT : mode;
        long agentSeed = agentSeed(entry, runSeed, ordinal);
        switch (selected) {
            case OFF -> configureOff(entry, agentSeed);
            case LIGHT -> configureLight(entry, agentSeed);
            case FULL -> configureFull(entry, agentSeed);
        }
        return agentSeed;
    }

    private static void configureOff(AgentRuntimeEntry entry, long seed) {
        MapleIslandObjectiveRandomnessRuntime.configure(entry,
                new MapleIslandObjectiveRandomnessSettings(
                        true, seed, NO_DELAY, NO_DELAY, false, false));
        AgentMapleIslandTravelRuntime.clear(entry);
    }

    private static void configureLight(AgentRuntimeEntry entry, long seed) {
        MapleIslandObjectiveRandomnessRuntime.configure(entry,
                new MapleIslandObjectiveRandomnessSettings(
                        true, seed, null, null, true, true));
        AgentMapleIslandTravelRuntime.configure(entry,
                new AgentMapleIslandTravelSettings(
                        seed, true, LIGHT_MAX_ROUTE_STRETCH, false, 0.0d,
                        sampleRange(seed ^ HOP_INTERVAL_DOMAIN, 2_500L, 4_500L), 0L));
    }

    private static void configureFull(AgentRuntimeEntry entry, long seed) {
        MapleIslandObjectiveRandomnessRuntime.configure(
                entry, MapleIslandObjectiveRandomnessSettings.cohort(seed));
        AgentMapleIslandTravelRuntime.configure(entry,
                new AgentMapleIslandTravelSettings(
                        seed, true, FULL_MAX_ROUTE_STRETCH, true, FULL_TRAVEL_HOP_PROBABILITY,
                        sampleRange(seed ^ HOP_INTERVAL_DOMAIN, 2_500L, 4_500L),
                        sampleRange(seed ^ HOP_COOLDOWN_DOMAIN, 8_000L, 15_000L)));
    }

    static long agentSeed(AgentRuntimeEntry entry, long runSeed, int ordinal) {
        long characterId = AgentRuntimeIdentityRuntime.hasBot(entry)
                ? Integer.toUnsignedLong(AgentRuntimeIdentityRuntime.bot(entry).getId()) : 0L;
        return mix(runSeed ^ (characterId << 32)
                ^ (ordinal * 0x9E3779B97F4A7C15L));
    }

    private static long sampleRange(long seed, long minimum, long maximum) {
        long width = maximum - minimum + 1L;
        return minimum + Long.remainderUnsigned(mix(seed), width);
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
