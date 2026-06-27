package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Temporary Agent-owned bridge to legacy bot scheduling side effects.
 */
public final class AgentBotSchedulerRuntime {
    private AgentBotSchedulerRuntime() {
    }

    public static AgentChatReportRuntime.ReportScheduler reportScheduler() {
        return AgentBotSchedulerRuntime::afterRandomDelay;
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        BotManager.scheduleBotReplyAction(randomDelayMs(minMs, maxMs), action);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        BotManager.scheduleBotReplyAction(delayMs, action);
    }

    static long randomDelayMs(int minMs, int maxMs) {
        return minMs + ThreadLocalRandom.current().nextInt(maxMs - minMs);
    }
}
