package server.agents.capabilities.objective;

import config.YamlConfig;

public record AgentObjectiveRecoveryPolicy(
        long nudgeAfterMs,
        long recoverAfterMs,
        int maxAutomaticRecoveries,
        long recoveryDelayMs) {

    public AgentObjectiveRecoveryPolicy {
        nudgeAfterMs = Math.max(0L, nudgeAfterMs);
        recoverAfterMs = recoverAfterMs <= 0L
                ? 0L : Math.max(nudgeAfterMs, recoverAfterMs);
        maxAutomaticRecoveries = Math.max(0, maxAutomaticRecoveries);
        recoveryDelayMs = Math.max(0L, recoveryDelayMs);
    }

    public static AgentObjectiveRecoveryPolicy configured() {
        return new AgentObjectiveRecoveryPolicy(
                YamlConfig.config.server.AGENT_OBJECTIVE_NUDGE_MS,
                YamlConfig.config.server.AGENT_OBJECTIVE_STALL_RECOVERY_MS,
                YamlConfig.config.server.AGENT_OBJECTIVE_AUTO_RECOVERY_ATTEMPTS,
                YamlConfig.config.server.AGENT_OBJECTIVE_RECOVERY_DELAY_MS);
    }
}
