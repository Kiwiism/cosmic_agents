package server.agents.capabilities.supplies;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for potion sharing state.
 */
public final class AgentPotionStateRuntime {
    private AgentPotionStateRuntime() {
    }

    public static boolean potShareRequested(AgentRuntimeEntry entry, boolean forHp) {
        return entry.potionSupplyState().shareRequested(forHp);
    }

    public static int potCheckTimerMs(AgentRuntimeEntry entry) {
        return entry.potionSupplyState().potCheckTimerMs();
    }

    public static boolean hasPotCheckDelay(AgentRuntimeEntry entry) {
        return potCheckTimerMs(entry) > 0;
    }

    public static void tickPotCheckDelay(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.potionSupplyState().setPotCheckTimerMs(
                tickDown.applyAsInt(entry.potionSupplyState().potCheckTimerMs()));
    }

    public static void setPotCheckTimerMs(AgentRuntimeEntry entry, int delayMs) {
        entry.potionSupplyState().setPotCheckTimerMs(delayMs);
    }

    public static void requestPotionCheckSoon(AgentRuntimeEntry entry, int soonDelayMs) {
        if (entry.potionSupplyState().potCheckTimerMs() <= 0
                || entry.potionSupplyState().potCheckTimerMs() > soonDelayMs) {
            entry.potionSupplyState().setPotCheckTimerMs(soonDelayMs);
        }
    }

    public static int mpRecoveryTimerMs(AgentRuntimeEntry entry) {
        return entry.potionSupplyState().mpRecoveryTimerMs();
    }

    public static boolean hasMpRecoveryDelay(AgentRuntimeEntry entry) {
        return mpRecoveryTimerMs(entry) > 0;
    }

    public static void tickMpRecoveryDelay(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.potionSupplyState().setMpRecoveryTimerMs(
                tickDown.applyAsInt(entry.potionSupplyState().mpRecoveryTimerMs()));
    }

    public static void setMpRecoveryTimerMs(AgentRuntimeEntry entry, int delayMs) {
        entry.potionSupplyState().setMpRecoveryTimerMs(delayMs);
    }

    public static void clearMpRecoveryTimer(AgentRuntimeEntry entry) {
        entry.potionSupplyState().setMpRecoveryTimerMs(0);
    }

    public static void setPotShareRequested(AgentRuntimeEntry entry, boolean forHp, boolean requested) {
        entry.potionSupplyState().setShareRequested(forHp, requested);
    }

    public static void clearPotShareRequested(AgentRuntimeEntry entry, boolean forHp) {
        setPotShareRequested(entry, forHp, false);
    }

    public static void clearAllPotShareRequests(AgentRuntimeEntry entry) {
        entry.potionSupplyState().clearAllShareRequests();
    }
}
