package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

public final class AgentBotCombatAlertRuntime {
    private static final long ALERT_DURATION_MS = 5000L;

    private AgentBotCombatAlertRuntime() {
    }

    public static void markAlerted(BotEntry entry) {
        AgentBotCombatCooldownStateRuntime.setAlertedUntilMs(entry, System.currentTimeMillis() + ALERT_DURATION_MS);
        scheduleAlertReset(entry);
    }

    private static void scheduleAlertReset(BotEntry entry) {
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
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                if (bot != null) {
                    bot.broadcastStance();
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
