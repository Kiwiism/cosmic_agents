package server.agents.capabilities.combat;

import server.agents.integration.AgentCombatStanceGateway;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned combat alert timing/state orchestration. Packet-visible stance
 * refresh remains behind the integration gateway.
 */
public final class AgentCombatAlertRuntime {
    private static final long ALERT_DURATION_MS = 5000L;

    private AgentCombatAlertRuntime() {
    }

    public static void markAlerted(AgentRuntimeEntry entry) {
        AgentCombatCooldownStateRuntime.setAlertedUntilMs(entry, System.currentTimeMillis() + ALERT_DURATION_MS);
        scheduleAlertReset(entry);
    }

    private static void scheduleAlertReset(AgentRuntimeEntry entry) {
        if (AgentCombatCooldownStateRuntime.alertResetScheduled(entry)) {
            return;
        }
        AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, true);
        long delay = Math.max(50L, AgentCombatCooldownStateRuntime.alertedUntilMs(entry) - System.currentTimeMillis() + 100L);
        AgentCombatRuntime.afterDelay(delay, () -> {
            long now = System.currentTimeMillis();
            if (now < AgentCombatCooldownStateRuntime.alertedUntilMs(entry)) {
                AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
                scheduleAlertReset(entry);
                return;
            }
            AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
            AgentCombatStanceGateway.broadcastCurrentStance(entry);
        });
    }
}
