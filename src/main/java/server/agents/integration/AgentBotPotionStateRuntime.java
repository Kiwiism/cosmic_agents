package server.agents.integration;

import server.bots.BotEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for potion sharing state.
 */
public final class AgentBotPotionStateRuntime {
    private AgentBotPotionStateRuntime() {
    }

    public static boolean potShareRequested(BotEntry entry, boolean forHp) {
        return entry.potionSupplyState().shareRequested(forHp);
    }

    public static int potCheckTimerMs(BotEntry entry) {
        return entry.potionSupplyState().potCheckTimerMs();
    }

    public static boolean hasPotCheckDelay(BotEntry entry) {
        return potCheckTimerMs(entry) > 0;
    }

    public static void tickPotCheckDelay(BotEntry entry, IntUnaryOperator tickDown) {
        entry.potionSupplyState().setPotCheckTimerMs(
                tickDown.applyAsInt(entry.potionSupplyState().potCheckTimerMs()));
    }

    public static void setPotCheckTimerMs(BotEntry entry, int delayMs) {
        entry.potionSupplyState().setPotCheckTimerMs(delayMs);
    }

    public static void requestPotionCheckSoon(BotEntry entry, int soonDelayMs) {
        if (entry.potionSupplyState().potCheckTimerMs() <= 0
                || entry.potionSupplyState().potCheckTimerMs() > soonDelayMs) {
            entry.potionSupplyState().setPotCheckTimerMs(soonDelayMs);
        }
    }

    public static int mpRecoveryTimerMs(BotEntry entry) {
        return entry.potionSupplyState().mpRecoveryTimerMs();
    }

    public static boolean hasMpRecoveryDelay(BotEntry entry) {
        return mpRecoveryTimerMs(entry) > 0;
    }

    public static void tickMpRecoveryDelay(BotEntry entry, IntUnaryOperator tickDown) {
        entry.potionSupplyState().setMpRecoveryTimerMs(
                tickDown.applyAsInt(entry.potionSupplyState().mpRecoveryTimerMs()));
    }

    public static void setMpRecoveryTimerMs(BotEntry entry, int delayMs) {
        entry.potionSupplyState().setMpRecoveryTimerMs(delayMs);
    }

    public static void clearMpRecoveryTimer(BotEntry entry) {
        entry.potionSupplyState().setMpRecoveryTimerMs(0);
    }

    public static void setPotShareRequested(BotEntry entry, boolean forHp, boolean requested) {
        entry.potionSupplyState().setShareRequested(forHp, requested);
    }

    public static void clearPotShareRequested(BotEntry entry, boolean forHp) {
        setPotShareRequested(entry, forHp, false);
    }

    public static void clearAllPotShareRequests(BotEntry entry) {
        entry.potionSupplyState().clearAllShareRequests();
    }
}
