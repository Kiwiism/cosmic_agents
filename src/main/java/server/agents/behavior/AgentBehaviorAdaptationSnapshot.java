package server.agents.behavior;

/** Immutable bounded state suitable for Director and presentation projections. */
public record AgentBehaviorAdaptationSnapshot(
        int energyPercent,
        int confidencePercent,
        int frustrationPercent,
        int restDebtPercent,
        int consecutiveMisses,
        long observedAtMs) {

    public AgentBehaviorAdaptationSnapshot {
        if (outside(energyPercent) || outside(confidencePercent)
                || outside(frustrationPercent) || outside(restDebtPercent)
                || consecutiveMisses < 0 || observedAtMs < 0L) {
            throw new IllegalArgumentException("valid bounded behavior snapshot is required");
        }
    }

    private static boolean outside(int value) {
        return value < 0 || value > 100;
    }
}
