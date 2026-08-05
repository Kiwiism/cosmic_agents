package server.agents.capabilities.combat;

/** Immutable observer-facing snapshot of an agent's current combat target. */
public record AgentCombatTargetTraceSnapshot(
        int characterId,
        String characterName,
        int mapId,
        long sampledAtMs,
        Position agentPosition,
        boolean hasTarget,
        int targetObjectId,
        int targetMobId,
        String targetName,
        Position targetPosition,
        int targetHpPercent,
        String action,
        String reasonCode,
        String reasonText,
        String objectiveId,
        String candidateClass,
        long selectedAtMs,
        int targetSwitchCount) {

    public AgentCombatTargetTraceSnapshot {
        characterName = text(characterName);
        targetName = text(targetName);
        action = text(action);
        reasonCode = text(reasonCode);
        reasonText = text(reasonText);
        objectiveId = text(objectiveId);
        candidateClass = text(candidateClass);
        agentPosition = agentPosition == null ? Position.missing() : agentPosition;
        targetPosition = targetPosition == null ? Position.missing() : targetPosition;
        targetHpPercent = Math.max(0, Math.min(100, targetHpPercent));
        targetSwitchCount = Math.max(0, targetSwitchCount);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record Position(boolean present, int x, int y) {
        public static Position missing() {
            return new Position(false, 0, 0);
        }
    }
}
