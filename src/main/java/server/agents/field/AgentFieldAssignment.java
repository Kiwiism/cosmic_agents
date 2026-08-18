package server.agents.field;

import java.awt.Point;
import java.util.Set;

/** Ephemeral output consumed by combat as a soft region lease. */
public record AgentFieldAssignment(
        String assignmentId,
        int mapId,
        int agentId,
        int partySlot,
        Set<String> cellIds,
        Set<Integer> regionIds,
        String stationId,
        Point anchor,
        int territoryMinX,
        int territoryMaxX,
        long expiresAtMs,
        long revision,
        String reason) {

    public AgentFieldAssignment {
        if (assignmentId == null || assignmentId.isBlank() || mapId < 0 || agentId <= 0
                || partySlot < 0 || cellIds == null || cellIds.isEmpty()
                || regionIds == null || regionIds.isEmpty() || stationId == null
                || stationId.isBlank() || anchor == null || territoryMinX > territoryMaxX
                || expiresAtMs < 0 || revision < 0 || reason == null) {
            throw new IllegalArgumentException("Valid field assignment identity, cells, regions, and lease are required");
        }
        cellIds = Set.copyOf(cellIds);
        regionIds = Set.copyOf(regionIds);
        anchor = new Point(anchor);
    }

    @Override
    public Point anchor() {
        return new Point(anchor);
    }
}
