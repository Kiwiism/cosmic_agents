package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentBotCombatAlertRuntime {
    private static final long ALERT_DURATION_MS = 5000L;

    private AgentBotCombatAlertRuntime() {
    }

    public static void markAlerted(AgentRuntimeEntry entry) {
        AgentBotCombatCooldownStateRuntime.setAlertedUntilMs(entry, System.currentTimeMillis() + ALERT_DURATION_MS);
        scheduleAlertReset(entry);
    }

    private static void scheduleAlertReset(AgentRuntimeEntry entry) {
        if (AgentBotCombatCooldownStateRuntime.alertResetScheduled(entry)) {
            return;
        }
        AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, true);
        long delay = Math.max(50L, AgentBotCombatCooldownStateRuntime.alertedUntilMs(entry) - System.currentTimeMillis() + 100L);
        AgentBotCombatRuntime.afterDelay(delay, () -> {
            long now = System.currentTimeMillis();
            if (now < AgentBotCombatCooldownStateRuntime.alertedUntilMs(entry)) {
                AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
                scheduleAlertReset(entry);
                return;
            }
            AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
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
