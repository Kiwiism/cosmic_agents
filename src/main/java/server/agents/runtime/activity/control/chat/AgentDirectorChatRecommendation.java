package server.agents.runtime.activity.control.chat;

import java.util.List;

/** Stable structured recommendation rendered by the panel; selection still creates a proposal. */
public record AgentDirectorChatRecommendation(
        int rank,
        String actionId,
        String label,
        String rationale,
        int mapId,
        String mapName,
        int catalogRank,
        int catalogWeight,
        int recommendedMinLevel,
        int recommendedMaxLevel,
        String terrain,
        List<String> tags,
        List<String> hazards,
        List<AgentDirectorDomainContext.SpawnFact> spawns,
        boolean selectable) {

    public AgentDirectorChatRecommendation {
        actionId = text(actionId);
        label = text(label);
        rationale = text(rationale);
        mapName = text(mapName);
        terrain = text(terrain);
        tags = List.copyOf(tags == null ? List.of() : tags);
        hazards = List.copyOf(hazards == null ? List.of() : hazards);
        spawns = List.copyOf(spawns == null ? List.of() : spawns);
        if (rank < 1 || actionId.isEmpty() || label.isEmpty() || rationale.isEmpty()
                || mapId <= 0 || mapName.isEmpty() || catalogRank < 1 || catalogWeight < 1
                || recommendedMinLevel < 1 || recommendedMaxLevel < recommendedMinLevel
                || terrain.isEmpty() || spawns.isEmpty()) {
            throw new IllegalArgumentException("complete Director recommendation is required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
