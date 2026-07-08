package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentBotCombatAlertRuntime {
    private static final long ALERT_DURATION_MS = 5000L;

    private AgentBotCombatAlertRuntime() {
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
        AgentBotCombatRuntime.afterDelay(delay, () -> {
            long now = System.currentTimeMillis();
            if (now < AgentCombatCooldownStateRuntime.alertedUntilMs(entry)) {
                AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
                scheduleAlertReset(entry);
                return;
            }
            AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
            try {
                Character bot = AgentRuntimeIdentityRuntime.bot(entry);
                if (bot != null) {
                    bot.broadcastStance();
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
