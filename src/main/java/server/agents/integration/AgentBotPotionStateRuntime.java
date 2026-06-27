package server.agents.integration;

import server.bots.BotEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for temporary BotEntry-backed potion sharing state.
 */
public final class AgentBotPotionStateRuntime {
    private AgentBotPotionStateRuntime() {
    }

    public static boolean potShareRequested(BotEntry entry, boolean forHp) {
        return forHp ? entry.potShareRequestedHp() : entry.potShareRequestedMp();
    }

    public static int potCheckTimerMs(BotEntry entry) {
        return entry.potCheckTimerMs();
    }

    public static boolean hasPotCheckDelay(BotEntry entry) {
        return potCheckTimerMs(entry) > 0;
    }

    public static void tickPotCheckDelay(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setPotCheckTimerMs(tickDown.applyAsInt(entry.potCheckTimerMs()));
    }

    public static void setPotCheckTimerMs(BotEntry entry, int delayMs) {
        entry.setPotCheckTimerMs(delayMs);
    }

    public static void requestPotionCheckSoon(BotEntry entry, int soonDelayMs) {
        if (entry.potCheckTimerMs() <= 0 || entry.potCheckTimerMs() > soonDelayMs) {
            entry.setPotCheckTimerMs(soonDelayMs);
        }
    }

    public static int mpRecoveryTimerMs(BotEntry entry) {
        return entry.mpRecoveryTimerMs();
    }

    public static boolean hasMpRecoveryDelay(BotEntry entry) {
        return mpRecoveryTimerMs(entry) > 0;
    }

    public static void tickMpRecoveryDelay(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setMpRecoveryTimerMs(tickDown.applyAsInt(entry.mpRecoveryTimerMs()));
    }

    public static void setMpRecoveryTimerMs(BotEntry entry, int delayMs) {
        entry.setMpRecoveryTimerMs(delayMs);
    }

    public static void clearMpRecoveryTimer(BotEntry entry) {
        entry.setMpRecoveryTimerMs(0);
    }

    public static void setPotShareRequested(BotEntry entry, boolean forHp, boolean requested) {
        if (forHp) {
            entry.setPotShareRequestedHp(requested);
        } else {
            entry.setPotShareRequestedMp(requested);
        }
    }

    public static void clearPotShareRequested(BotEntry entry, boolean forHp) {
        setPotShareRequested(entry, forHp, false);
    }

    public static void clearAllPotShareRequests(BotEntry entry) {
        entry.setPotShareRequestedHp(false);
        entry.setPotShareRequestedMp(false);
    }
}
