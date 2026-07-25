package server.agents.capabilities.objective;

import config.YamlConfig;

public record AgentObjectiveRecoveryPolicy(
        long nudgeAfterMs,
        long recoverAfterMs,
        int maxAutomaticRecoveries,
        long recoveryDelayMs) {
    private static final long COMBAT_STALL_RECOVERY_MIN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.objective.AgentObjectiveRecoveryPolicy.COMBAT_STALL_RECOVERY_MIN_MS");
    private static final long COMBAT_STALL_RECOVERY_PER_CROWD_CHARACTER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.objective.AgentObjectiveRecoveryPolicy.COMBAT_STALL_RECOVERY_PER_CROWD_CHARACTER_MS");
    private static final long COMBAT_STALL_RECOVERY_MAX_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.objective.AgentObjectiveRecoveryPolicy.COMBAT_STALL_RECOVERY_MAX_MS");

    public AgentObjectiveRecoveryPolicy {
        nudgeAfterMs = Math.max(0L, nudgeAfterMs);
        recoverAfterMs = recoverAfterMs <= 0L
                ? 0L : Math.max(nudgeAfterMs, recoverAfterMs);
        maxAutomaticRecoveries = Math.max(0, maxAutomaticRecoveries);
        recoveryDelayMs = Math.max(0L, recoveryDelayMs);
    }

    public static AgentObjectiveRecoveryPolicy configured() {
        return new AgentObjectiveRecoveryPolicy(
                config.AgentYamlConfig.config.agent.AGENT_OBJECTIVE_NUDGE_MS,
                config.AgentYamlConfig.config.agent.AGENT_OBJECTIVE_STALL_RECOVERY_MS,
                config.AgentYamlConfig.config.agent.AGENT_OBJECTIVE_AUTO_RECOVERY_ATTEMPTS,
                config.AgentYamlConfig.config.agent.AGENT_OBJECTIVE_RECOVERY_DELAY_MS);
    }

    public AgentObjectiveRecoveryPolicy forCombatCrowd(int charactersAboveQuietThreshold) {
        long crowdExtension = Math.max(0, charactersAboveQuietThreshold)
                * COMBAT_STALL_RECOVERY_PER_CROWD_CHARACTER_MS;
        long crowdAdjustedRecoveryMs = COMBAT_STALL_RECOVERY_MIN_MS + crowdExtension;
        if (COMBAT_STALL_RECOVERY_MAX_MS > 0L) {
            crowdAdjustedRecoveryMs = Math.min(
                    crowdAdjustedRecoveryMs, COMBAT_STALL_RECOVERY_MAX_MS);
        }
        long combatRecoveryMs = Math.max(recoverAfterMs, crowdAdjustedRecoveryMs);
        return new AgentObjectiveRecoveryPolicy(
                nudgeAfterMs, combatRecoveryMs, maxAutomaticRecoveries, recoveryDelayMs);
    }
}
