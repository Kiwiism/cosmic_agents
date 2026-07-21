package server.agents.capabilities.dialogue.conversation;

import server.agents.runtime.AgentRuntimeEntry;

/** Service-provider boundary for plan or region activity exposed to generic dialogue models. */
public interface AgentConversationActivityProvider {
    AgentConversationActivity snapshot(AgentRuntimeEntry entry, long nowMs);
}
