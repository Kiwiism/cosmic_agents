package server.agents.runtime.activity.control;

import server.agents.behavior.AgentBehaviorAdaptationSnapshot;

/** Human-readable energy and adaptation values exposed to Director clients. */
public record AgentDirectorEnergySnapshot(
        int energyPercent,
        int restDebtPercent,
        int confidencePercent,
        int frustrationPercent,
        String band,
        long observedAtMs) {

    public static AgentDirectorEnergySnapshot from(AgentBehaviorAdaptationSnapshot state) {
        if (state == null) throw new IllegalArgumentException("behavior state is required");
        int energy = state.energyPercent();
        String band = energy < 20 ? "EXHAUSTED"
                : energy < 40 ? "LOW"
                : energy < 70 ? "STEADY" : "RESTED";
        return new AgentDirectorEnergySnapshot(
                energy, state.restDebtPercent(), state.confidencePercent(),
                state.frustrationPercent(), band, state.observedAtMs());
    }
}
