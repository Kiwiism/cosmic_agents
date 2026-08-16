package server.agents.operations.events;

import server.agents.events.AgentContextualEvent;

import java.awt.Point;

/** Presentation-safe combat posture evidence; it never participates in attack legality. */
public record AgentCombatPostureChangedEvent(
        int agentId,
        long occurredAtMs,
        int mapId,
        Posture posture,
        int targetMobId,
        Point targetPosition,
        String reason,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "combat.posture-changed";

    public AgentCombatPostureChangedEvent {
        targetPosition = targetPosition == null ? new Point() : new Point(targetPosition);
        reason = reason == null ? "" : reason.trim();
        objectiveId = objectiveId == null ? "" : objectiveId.trim();
        if (agentId <= 0 || occurredAtMs < 0L || mapId <= 0 || posture == null
                || targetMobId < 0) {
            throw new IllegalArgumentException("valid combat posture event fields are required");
        }
    }

    @Override
    public Point targetPosition() {
        return new Point(targetPosition);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return mapId + ":posture:" + posture;
    }

    public enum Posture {
        SEARCHING,
        MELEE,
        RANGED,
        SAFE_SHOT,
        KITING,
        JUMP_ATTACK,
        AOE_REPOSITION,
        LOOTING,
        IDLE
    }
}
