package server.agents.runtime.activity.control.chat;

import java.util.List;

/** Bounded, immutable catalog evidence supplied to the high-level Director model. */
public record AgentDirectorDomainContext(
        String domain,
        String gameDataVersion,
        int requestedLevel,
        int agentLevel,
        int requestedCount,
        List<TrainingMapCandidate> trainingMaps) {

    public AgentDirectorDomainContext {
        domain = text(domain);
        gameDataVersion = text(gameDataVersion);
        requestedCount = Math.max(1, Math.min(5, requestedCount));
        trainingMaps = List.copyOf(trainingMaps == null ? List.of() : trainingMaps);
        if (domain.isEmpty() || gameDataVersion.isEmpty()
                || requestedLevel < 1 || agentLevel < 1) {
            throw new IllegalArgumentException("complete Director domain context is required");
        }
    }

    public record TrainingMapCandidate(
            String actionId,
            String label,
            int mapId,
            String mapName,
            int catalogRank,
            int catalogWeight,
            int recommendedMinLevel,
            int recommendedMaxLevel,
            int recommendedAgents,
            int maximumAgents,
            String terrain,
            String catalogRationale,
            List<String> conditions,
            List<String> tags,
            List<String> hazards,
            List<SpawnFact> spawns,
            boolean selectable) {

        public TrainingMapCandidate {
            actionId = text(actionId);
            label = text(label);
            mapName = text(mapName);
            terrain = text(terrain);
            catalogRationale = text(catalogRationale);
            conditions = List.copyOf(conditions == null ? List.of() : conditions);
            tags = List.copyOf(tags == null ? List.of() : tags);
            hazards = List.copyOf(hazards == null ? List.of() : hazards);
            spawns = List.copyOf(spawns == null ? List.of() : spawns);
            if (actionId.isEmpty() || label.isEmpty() || mapId <= 0 || mapName.isEmpty()
                    || catalogRank < 1 || catalogWeight < 1
                    || recommendedMinLevel < 1
                    || recommendedMaxLevel < recommendedMinLevel
                    || recommendedAgents < 1 || maximumAgents < recommendedAgents
                    || terrain.isEmpty() || catalogRationale.isEmpty() || spawns.isEmpty()) {
                throw new IllegalArgumentException("complete training-map evidence is required");
            }
        }
    }

    public record SpawnFact(
            int mobId,
            String mobName,
            int mobLevel,
            int expectedCount,
            String role) {
        public SpawnFact {
            mobName = text(mobName);
            role = text(role);
            if (mobId <= 0 || mobName.isEmpty() || mobLevel < 1
                    || expectedCount < 1 || role.isEmpty()) {
                throw new IllegalArgumentException("complete spawn evidence is required");
            }
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
