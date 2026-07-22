package server.agents.plans.mapleisland.cohort;

import config.YamlConfig;
import server.agents.capabilities.combat.AgentCombatVariationRuntime;
import server.agents.capabilities.combat.AgentCombatVariationSettings;
import server.agents.capabilities.navigation.AgentTravelVariationRuntime;
import server.agents.capabilities.navigation.AgentTravelVariationSettings;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.capabilities.presentation.AgentPersonalityPresentationRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorRuntime;

/** Applies one deterministic, run-scoped realism preset to a cohort Agent. */
public final class MapleIslandCohortRealismService {
    private static final double LIGHT_MAX_ROUTE_STRETCH = 1.08d;
    private static final double FULL_MAX_ROUTE_STRETCH = 1.15d;
    private static final double FULL_TRAVEL_HOP_PROBABILITY = 0.04d;
    private static final AgentBehaviorProfile.DelayRange NO_DELAY =
            new AgentBehaviorProfile.DelayRange(0, 0);
    private static final long HOP_INTERVAL_DOMAIN = 0x484F502D494E5456L;
    private static final long HOP_COOLDOWN_DOMAIN = 0x484F502D434F4F4CL;
    private static final long INITIAL_INTENTION_DELAY_DOMAIN = 0x535041574E2D5741L;
    private static final long INITIAL_INTENTION_DELAY_MIN_MS = 2_000L;
    private static final long INITIAL_INTENTION_DELAY_MAX_MS = 5_000L;

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
                        true, seed, NO_DELAY, NO_DELAY, false, false, false));
        AgentTravelVariationRuntime.clear(entry);
        AgentCombatVariationRuntime.clear(entry);
    }

    private static void configureLight(AgentRuntimeEntry entry, long seed) {
        MapleIslandObjectiveRandomnessRuntime.configure(entry,
                new MapleIslandObjectiveRandomnessSettings(
                        true, seed, null, null, true, true, true));
        AgentTravelVariationRuntime.configure(entry,
                new AgentTravelVariationSettings(
                        seed, true, LIGHT_MAX_ROUTE_STRETCH, false, 0.0d,
                        sampleRange(seed ^ HOP_INTERVAL_DOMAIN, 2_500L, 4_500L), 0L));
        AgentCombatVariationRuntime.clear(entry);
    }

    private static void configureFull(AgentRuntimeEntry entry, long seed) {
        AgentBehaviorPolicyProfile behaviorPolicy = AgentBehaviorRuntime.enabled(entry)
                && YamlConfig.config.server.AGENT_NAVIGATION_BEHAVIOR_ENABLED
                ? AgentBehaviorRuntime.policy(entry) : null;
        double routeStretch = behaviorPolicy == null
                ? FULL_MAX_ROUTE_STRETCH : behaviorPolicy.navigation().maxRouteStretch();
        double hopProbability = behaviorPolicy == null
                ? AgentPersonalityPresentationRuntime.travelHopProbability(entry, FULL_TRAVEL_HOP_PROBABILITY)
                : percentProbability(behaviorPolicy.navigation().travelJumpPercent());
        MapleIslandObjectiveRandomnessRuntime.configure(
                entry, MapleIslandObjectiveRandomnessSettings.cohort(seed));
        AgentTravelVariationRuntime.configure(entry,
                new AgentTravelVariationSettings(
                        seed, true, routeStretch, true, hopProbability,
                        sampleRange(seed ^ HOP_INTERVAL_DOMAIN, 2_500L, 4_500L),
                        sampleRange(seed ^ HOP_COOLDOWN_DOMAIN, 8_000L, 15_000L)));
        AgentCombatVariationRuntime.configure(entry,
                new AgentCombatVariationSettings(
                        seed, true,
                        percentProbability(YamlConfig.config.server.AGENT_MAPLE_ISLAND_FULL_MIDDLE_TARGET_PERCENT),
                        Math.clamp(YamlConfig.config.server.AGENT_MAPLE_ISLAND_FULL_TARGET_SHORTLIST_LIMIT, 1, 64),
                        true,
                        percentProbability(YamlConfig.config.server.AGENT_MAPLE_ISLAND_FULL_PLATFORM_ANCHOR_PERCENT)));
    }

    static long agentSeed(AgentRuntimeEntry entry, long runSeed, int ordinal) {
        long characterId = AgentRuntimeIdentityRuntime.hasBot(entry)
                ? Integer.toUnsignedLong(AgentRuntimeIdentityRuntime.bot(entry).getId()) : 0L;
        return mix(runSeed ^ (characterId << 32)
                ^ (ordinal * 0x9E3779B97F4A7C15L));
    }

    static long initialIntentionDelayMs(long agentSeed) {
        return sampleRange(agentSeed ^ INITIAL_INTENTION_DELAY_DOMAIN,
                INITIAL_INTENTION_DELAY_MIN_MS, INITIAL_INTENTION_DELAY_MAX_MS);
    }

    private static long sampleRange(long seed, long minimum, long maximum) {
        long width = maximum - minimum + 1L;
        return minimum + Long.remainderUnsigned(mix(seed), width);
    }

    private static double percentProbability(int configuredPercent) {
        return Math.clamp(configuredPercent, 0, 100) / 100.0d;
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
