package server.agents.capabilities.dialogue.conversation;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicDefinition;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pluggable utility-selected conversation topic with deterministic storylet output. */
public interface AgentConversationTopicModel {
    int UNAVAILABLE = Integer.MIN_VALUE;

    AgentDialogueTopicDefinition definition();

    Map<String, List<String>> templates();

    int utility(AgentConversationSelectionContext context);

    AgentSemanticDialogueAct produce(AgentConversationModelContext context);

    default AgentSemanticDialogueAct act(AgentConversationModelContext context,
                                          String actKey,
                                          Map<String, String> parameters) {
        Character speaker = context.speaker().bot();
        Character listener = context.listener().bot();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("speakerName", speaker.getName());
        values.put("listenerName", listener.getName());
        values.put("mapId", String.valueOf(speaker.getMapId()));
        if (parameters != null) {
            values.putAll(parameters);
        }
        return new AgentSemanticDialogueAct(
                speaker.getId(), listener.getId(), context.nowMs(), definition().topicId(), actKey,
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "conversation:" + context.conversationId() + ":" + context.turnIndex(),
                0L, context.variationSeed(), values);
    }
}
