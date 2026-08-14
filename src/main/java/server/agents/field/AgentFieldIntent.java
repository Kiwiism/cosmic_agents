package server.agents.field;

import java.util.Map;
import java.util.Set;

/** Plan-facing reason for participating in one map's farming allocation. */
public record AgentFieldIntent(
        Type type,
        String objectiveId,
        Set<Integer> requiredMobIds,
        Map<Integer, Integer> requiredKills,
        boolean temporaryVisitor) {

    public enum Type {
        FREE_GRIND,
        QUEST_VISITOR,
        PARTY_COVERAGE,
        ANCHOR,
        SUPPORT,
        TRANSIT
    }

    public AgentFieldIntent {
        if (type == null || objectiveId == null || requiredMobIds == null || requiredKills == null) {
            throw new IllegalArgumentException("Field intent type, objective, and targets are required");
        }
        requiredMobIds = Set.copyOf(requiredMobIds);
        requiredKills = Map.copyOf(requiredKills);
        if (requiredKills.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
            throw new IllegalArgumentException("Field objective kill requirements must be positive");
        }
    }

    public static AgentFieldIntent freeGrind(String objectiveId) {
        return new AgentFieldIntent(Type.FREE_GRIND, objectiveId, Set.of(), Map.of(), false);
    }

    public static AgentFieldIntent partyCoverage(
            String objectiveId, Set<Integer> mobIds, Map<Integer, Integer> requiredKills) {
        return new AgentFieldIntent(
                Type.PARTY_COVERAGE, objectiveId, mobIds, requiredKills, false);
    }

    public static AgentFieldIntent questVisitor(String objectiveId, Set<Integer> mobIds) {
        return new AgentFieldIntent(Type.QUEST_VISITOR, objectiveId, mobIds, Map.of(), true);
    }

    public boolean acceptsMob(int mobId) {
        return requiredMobIds.isEmpty() || requiredMobIds.contains(mobId);
    }
}
