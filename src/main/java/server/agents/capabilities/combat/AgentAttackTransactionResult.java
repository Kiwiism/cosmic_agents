package server.agents.capabilities.combat;

import java.util.List;

/** Result of one server-authoritative Agent attack attempt. */
public record AgentAttackTransactionResult(Status status,
                                           Reason reason,
                                           int mapId,
                                           int skillId,
                                           List<Integer> targetObjectIds,
                                           int hitLines,
                                           int missLines,
                                           long committedAtMs) {
    public enum Status {
        COMMITTED,
        DEFERRED,
        REJECTED
    }

    public enum Reason {
        NONE,
        INVALID_REQUEST,
        TARGET_UNAVAILABLE,
        TARGET_NOT_IN_AGENT_MAP,
        ATTACK_COOLDOWN,
        NO_AMMO,
        CANNOT_USE_SKILL,
        CANNOT_USE_ATTACK_PLAN,
        MAP_CHANGED_DURING_ATTACK,
        HANDLER_REJECTED
    }

    public AgentAttackTransactionResult {
        targetObjectIds = List.copyOf(targetObjectIds);
    }

    public boolean committed() {
        return status == Status.COMMITTED;
    }

    static AgentAttackTransactionResult committed(int mapId,
                                                   int skillId,
                                                   List<Integer> targetObjectIds,
                                                   int hitLines,
                                                   int missLines,
                                                   long committedAtMs) {
        return new AgentAttackTransactionResult(
                Status.COMMITTED,
                Reason.NONE,
                mapId,
                skillId,
                targetObjectIds,
                hitLines,
                missLines,
                committedAtMs);
    }

    static AgentAttackTransactionResult deferred(Reason reason, int mapId, int skillId) {
        return incomplete(Status.DEFERRED, reason, mapId, skillId);
    }

    static AgentAttackTransactionResult rejected(Reason reason, int mapId, int skillId) {
        return incomplete(Status.REJECTED, reason, mapId, skillId);
    }

    private static AgentAttackTransactionResult incomplete(Status status, Reason reason, int mapId, int skillId) {
        return new AgentAttackTransactionResult(status, reason, mapId, skillId, List.of(), 0, 0, 0L);
    }
}
