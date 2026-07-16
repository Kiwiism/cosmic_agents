package server.agents.capabilities.objective;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.OptionalInt;
import java.util.OptionalLong;

public final class MapleIslandObjectiveRandomnessRuntime {
    private MapleIslandObjectiveRandomnessRuntime() {
    }

    public static void configure(AgentRuntimeEntry entry,
                                 MapleIslandObjectiveRandomnessSettings settings) {
        if (entry != null) {
            entry.mapleIslandObjectiveRandomnessState().configure(settings, agentId(entry));
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        configure(entry, MapleIslandObjectiveRandomnessSettings.disabled());
    }

    public static MapleIslandObjectiveRandomnessSettings settings(AgentRuntimeEntry entry) {
        return entry == null
                ? MapleIslandObjectiveRandomnessSettings.disabled()
                : entry.mapleIslandObjectiveRandomnessState().settings();
    }

    public static OptionalLong sampleNpcInteractionDelayMs(
            AgentRuntimeEntry entry,
            AgentBehaviorProfile.DelayRange fallbackRange) {
        if (!settings(entry).enabled()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.mapleIslandObjectiveRandomnessState()
                .sampleNpcDelay(fallbackRange));
    }

    public static OptionalLong sampleBetweenObjectivesDelayMs(
            AgentRuntimeEntry entry,
            AgentBehaviorProfile.DelayRange fallbackRange) {
        if (!settings(entry).enabled()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.mapleIslandObjectiveRandomnessState()
                .sampleObjectiveDelay(fallbackRange));
    }

    public static OptionalInt selectNpcAnchorIndex(
            AgentRuntimeEntry entry,
            int mapId,
            int npcId,
            int candidateCount) {
        MapleIslandObjectiveRandomnessSettings settings = settings(entry);
        if (!settings.enabled() || !settings.npcAnchorVariationEnabled() || candidateCount <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(entry.mapleIslandObjectiveRandomnessState()
                .selectNpcAnchorIndex(mapId, npcId, candidateCount));
    }

    public static OptionalLong sampleCashShopVisitDelayMs(
            AgentRuntimeEntry entry,
            long minimumMs,
            long maximumMs) {
        if (!settings(entry).enabled()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.mapleIslandObjectiveRandomnessState()
                .sampleCashShopVisitDelay(minimumMs, maximumMs));
    }

    public static OptionalInt selectRestSpotIndex(
            AgentRuntimeEntry entry,
            int mapId,
            int candidateCount) {
        MapleIslandObjectiveRandomnessSettings settings = settings(entry);
        if (!settings.enabled() || candidateCount <= 0) {
            return OptionalInt.empty();
        }
        if (!settings.restSpotVariationEnabled()) {
            return OptionalInt.of(0);
        }
        return OptionalInt.of(entry.mapleIslandObjectiveRandomnessState()
                .selectRestSpotIndex(mapId, candidateCount));
    }

    public static OptionalInt selectRestFacingDirection(
            AgentRuntimeEntry entry,
            int mapId) {
        MapleIslandObjectiveRandomnessSettings settings = settings(entry);
        if (!settings.enabled()) {
            return OptionalInt.empty();
        }
        if (!settings.restSpotVariationEnabled()) {
            return OptionalInt.of(1);
        }
        return OptionalInt.of(entry.mapleIslandObjectiveRandomnessState()
                .selectRestFacingDirection(mapId));
    }

    private static long agentId(AgentRuntimeEntry entry) {
        return entry != null && AgentRuntimeIdentityRuntime.hasBot(entry)
                ? AgentRuntimeIdentityRuntime.bot(entry).getId()
                : 0L;
    }
}
