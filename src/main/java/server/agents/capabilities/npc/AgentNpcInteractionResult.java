package server.agents.capabilities.npc;

import server.agents.capabilities.AgentCapabilityStatus;

import java.awt.Point;

public record AgentNpcInteractionResult(
        boolean success,
        AgentCapabilityStatus status,
        String message,
        int npcId,
        int mapId,
        Point npcPosition,
        Point approachPoint,
        long estimatedDelayMs) {

    public AgentNpcInteractionResult {
        if (npcPosition != null) {
            npcPosition = new Point(npcPosition);
        }
        if (approachPoint != null) {
            approachPoint = new Point(approachPoint);
        }
    }

    public static AgentNpcInteractionResult success(String message, AgentNpcInteractionRequest request,
            Point npcPosition, Point approachPoint, long estimatedDelayMs) {
        return new AgentNpcInteractionResult(true, AgentCapabilityStatus.SUCCESS, message,
                request.npcId(), request.mapId(), npcPosition, approachPoint, estimatedDelayMs);
    }

    public static AgentNpcInteractionResult pending(String message, AgentNpcInteractionRequest request,
            Point npcPosition, Point approachPoint, long estimatedDelayMs) {
        return new AgentNpcInteractionResult(false, AgentCapabilityStatus.NOT_READY, message,
                request.npcId(), request.mapId(), npcPosition, approachPoint, estimatedDelayMs);
    }

    public static AgentNpcInteractionResult blocked(AgentCapabilityStatus status, String message,
            AgentNpcInteractionRequest request) {
        int npcId = request == null ? 0 : request.npcId();
        int mapId = request == null ? 0 : request.mapId();
        return new AgentNpcInteractionResult(false, status, message, npcId, mapId, null, null, 0);
    }
}
