package server.agents.capabilities.dialogue.conversation;

import server.agents.runtime.AgentRuntimeEntry;

public record AgentConversationModelContext(
        AgentRuntimeEntry speaker,
        AgentRuntimeEntry listener,
        long conversationId,
        String selectedTopicId,
        int turnIndex,
        int maxTurns,
        long nowMs,
        long variationSeed) {
}
