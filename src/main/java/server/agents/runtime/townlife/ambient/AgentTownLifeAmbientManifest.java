package server.agents.runtime.townlife.ambient;

import server.agents.capabilities.townlife.AgentTownLifeAmbientPolicy;
import server.agents.capabilities.townlife.AgentTownLifeState;

import java.util.List;
import java.util.Map;

/** Deployment-only policy; the TownLife capability remains unaware of pool mechanics. */
public record AgentTownLifeAmbientManifest(
        int schemaVersion,
        int defaultPoolSize,
        int targetActivePercent,
        long rebalanceEveryMs,
        long gracefulExitMs,
        StandbyMode standbyMode,
        List<Integer> chairItemIds,
        List<Town> towns,
        AgentTownLifeAmbientPolicy.TransitionWeights transitions) {

    public enum StandbyMode { UNMATERIALIZED, VISIBLE }

    public AgentTownLifeAmbientManifest {
        standbyMode = standbyMode == null ? StandbyMode.UNMATERIALIZED : standbyMode;
        chairItemIds = List.copyOf(chairItemIds == null ? List.of() : chairItemIds);
        towns = List.copyOf(towns == null ? List.of() : towns);
        if (schemaVersion != 1 || defaultPoolSize <= 0 || targetActivePercent < 0
                || targetActivePercent > 100 || rebalanceEveryMs <= 0L || gracefulExitMs <= 0L
                || towns.isEmpty() || transitions == null) {
            throw new IllegalArgumentException("invalid ambient TownLife deployment manifest");
        }
    }

    public record Town(String profileId, int mapId, int allocationWeight,
                       int minActive, int maxActive,
                       Map<AgentTownLifeState.Activity,
                               AgentTownLifeAmbientPolicy.ActivityRule> activities) {
        public Town {
            profileId = profileId == null ? "" : profileId.trim();
            activities = Map.copyOf(activities == null ? Map.of() : activities);
            if (profileId.isBlank() || mapId <= 0 || allocationWeight <= 0 || minActive < 0
                    || maxActive < minActive || activities.isEmpty()) {
                throw new IllegalArgumentException("invalid ambient TownLife town policy");
            }
        }

        public AgentTownLifeAmbientPolicy behavior(
                AgentTownLifeAmbientPolicy.TransitionWeights transitions,
                List<Integer> chairItemIds) {
            return new AgentTownLifeAmbientPolicy(activities, transitions, chairItemIds);
        }
    }
}
