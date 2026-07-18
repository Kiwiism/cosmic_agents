package server.agents.catalog;

import java.util.List;

public record AgentMapRegionAssignment(
        String assignmentId,
        int mapId,
        List<String> regionIds,
        int partySlot,
        int capacity,
        long expiresAtMs) {

    public AgentMapRegionAssignment {
        if (assignmentId == null || assignmentId.isBlank() || mapId < 0 || regionIds == null
                || regionIds.isEmpty() || partySlot < 0 || capacity <= 0 || expiresAtMs < 0) {
            throw new IllegalArgumentException("Valid map region assignment fields are required");
        }
        regionIds = List.copyOf(regionIds);
    }
}
