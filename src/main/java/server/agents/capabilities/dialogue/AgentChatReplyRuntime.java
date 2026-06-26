package server.agents.capabilities.dialogue;

import server.agents.commands.AgentReplyQueue;

public final class AgentChatReplyRuntime {
    private AgentChatReplyRuntime() {
    }

    public static void queueSay(AgentReplyQueue.State state, String message, AgentReplyQueue.Dispatcher dispatcher) {
        queueSayWithEstimatedDelay(state, message, dispatcher);
    }

    public static void queueReply(AgentReplyQueue.State state, String message, AgentReplyQueue.Dispatcher dispatcher) {
        queueReplyWithEstimatedDelay(state, message, dispatcher);
    }

    public static long queueSayWithEstimatedDelay(
            AgentReplyQueue.State state,
            String message,
            AgentReplyQueue.Dispatcher dispatcher) {
        return AgentReplyQueue.queueSay(state, message, dispatcher);
    }

    public static long queueReplyWithEstimatedDelay(
            AgentReplyQueue.State state,
            String message,
            AgentReplyQueue.Dispatcher dispatcher) {
        return AgentReplyQueue.queueReply(state, message, dispatcher);
    }
}
