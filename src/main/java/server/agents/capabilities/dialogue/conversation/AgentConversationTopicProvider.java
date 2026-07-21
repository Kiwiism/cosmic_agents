package server.agents.capabilities.dialogue.conversation;

import java.util.List;

/** Service-provider boundary for region, event, or future model-plugin storylets. */
public interface AgentConversationTopicProvider {
    List<AgentConversationTopicModel> topicModels();
}
