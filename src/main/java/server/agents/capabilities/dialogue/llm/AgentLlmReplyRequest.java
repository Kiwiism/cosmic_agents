package server.agents.capabilities.dialogue.llm;

import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeHandle;

public record AgentLlmReplyRequest<E extends AgentRuntimeHandle>(
        E entry,
        int agentId,
        String agentName,
        AgentReplyChannel replyChannel,
        AgentSenderRelation relation,
        AgentLlmPromptContext promptContext) {
}
