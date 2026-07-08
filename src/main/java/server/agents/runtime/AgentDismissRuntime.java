package server.agents.runtime;

import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import java.util.List;
import java.util.function.Consumer;

/**
 * Temporary legacy hook bundle for dismiss lifecycle side effects while
 * mode-command entry points still use compatibility-shaped callbacks.
 */
public final class AgentDismissRuntime {
    private static final List<String> FAREWELL_MESSAGES = List.of(
            "ok", "sure", "alright", "gotcha",
            "later!", "see ya", "take care", "cya", "peace out");

    private AgentDismissRuntime() {
    }

    public static boolean dismissAgentByName(int leaderCharId, String agentName, Consumer<AgentRuntimeEntry> stopAgent) {
        return AgentLifecycleService.dismissAgentByName(
                leaderCharId,
                agentName,
                new AgentLifecycleService.DismissHooks(
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        stopAgent,
                        AgentSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(400, 600),
                        AgentReplyRuntime::replyNow,
                        () -> AgentDialogueSelector.randomReply(FAREWELL_MESSAGES)));
    }
}
