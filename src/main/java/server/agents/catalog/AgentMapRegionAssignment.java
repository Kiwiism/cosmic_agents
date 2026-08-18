package server.agents.catalog;

import java.util.List;

public record AgentMapRegionAssignment(
        String assignmentId,
        int mapId,
        List<String> regionIds,
        int partySlot,
        int capacity,
        long expiresAtMs,
        long emptyBorrowDelayMs,
        int territoryMinX,
        int territoryMaxX,
        boolean territorial) {

    public AgentMapRegionAssignment {
        if (assignmentId == null || assignmentId.isBlank() || mapId < 0 || regionIds == null
                || regionIds.isEmpty() || partySlot < 0 || capacity <= 0 || expiresAtMs < 0) {
            throw new IllegalArgumentException("Valid map region assignment fields are required");
        }
        if (emptyBorrowDelayMs < 0) {
            throw new IllegalArgumentException("Region assignment empty-borrow delay cannot be negative");
        }
        if (territorial && territoryMinX > territoryMaxX) {
            throw new IllegalArgumentException("Territorial assignment bounds are invalid");
        }
        regionIds = List.copyOf(regionIds);
    }

    public AgentMapRegionAssignment(
            String assignmentId,
            int mapId,
            List<String> regionIds,
            int partySlot,
            int capacity,
            long expiresAtMs) {
        this(assignmentId, mapId, regionIds, partySlot, capacity, expiresAtMs,
                0L, 0, 0, false);
    }

    public AgentMapRegionAssignment(
            String assignmentId,
            int mapId,
            List<String> regionIds,
            int partySlot,
            int capacity,
            long expiresAtMs,
            long emptyBorrowDelayMs) {
        this(assignmentId, mapId, regionIds, partySlot, capacity, expiresAtMs,
                emptyBorrowDelayMs, 0, 0, false);
    }
}
