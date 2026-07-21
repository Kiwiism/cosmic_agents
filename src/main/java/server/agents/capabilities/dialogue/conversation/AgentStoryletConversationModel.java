package server.agents.capabilities.dialogue.conversation;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.semantic.AgentDialogueModel;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;

/** Utility-selected starter storylets with deterministic template variation. */
public final class AgentStoryletConversationModel
        implements AgentDialogueModel<AgentConversationModelContext> {
    @Override
    public String modelId() {
        return "storylet.utility.v1";
    }

    @Override
    public AgentSemanticDialogueAct produce(AgentConversationModelContext context) {
        AgentConversationTopicRegistry.ensureLoaded();
        Character speaker = context.speaker().bot();
        Character listener = context.listener().bot();
        boolean first = context.turnIndex() == 0;
        boolean last = context.turnIndex() == context.maxTurns() - 1;
        if (first) {
            if (AgentDialogueTopicRegistry.enabled(AgentDialogueTopicRegistry.GREETING)) {
                return framing(context, AgentDialogueTopicRegistry.GREETING, "open", speaker, listener);
            }
        } else if (last && AgentDialogueTopicRegistry.enabled(AgentDialogueTopicRegistry.FAREWELL)) {
            return framing(context, AgentDialogueTopicRegistry.FAREWELL, "close", speaker, listener);
        }
        AgentSemanticDialogueAct content = AgentDialogueTopicRegistry.enabled(context.selectedTopicId())
                ? AgentConversationTopicRegistry.produce(context.selectedTopicId(), context) : null;
        if (content != null) {
            return content;
        }
        AgentSemanticDialogueAct fallback = AgentConversationTopicRegistry.produce(
                AgentDialogueTopicRegistry.ENCOURAGEMENT, context);
        return fallback != null ? fallback
                : framing(context, AgentDialogueTopicRegistry.GREETING, "open", speaker, listener);
    }

    private static AgentSemanticDialogueAct framing(AgentConversationModelContext context,
                                                     String topicId,
                                                     String actKey,
                                                     Character speaker,
                                                     Character listener) {
        return new AgentSemanticDialogueAct(
                speaker.getId(), listener.getId(), context.nowMs(), topicId, actKey,
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "conversation:" + context.conversationId() + ":" + context.turnIndex(),
                0L, context.variationSeed(),
                Map.of("speakerName", speaker.getName(), "listenerName", listener.getName(),
                        "mapId", String.valueOf(speaker.getMapId())));
    }

    public String selectTopic(AgentRuntimeEntry speaker,
                              AgentRuntimeEntry listener,
                              long nowMs,
                              long seed) {
        AgentConversationTopicRegistry.ensureLoaded();
        return AgentConversationTopicRegistry.selectTopic(
                new AgentConversationSelectionContext(speaker, listener, nowMs, seed));
    }

    public int sociability(AgentRuntimeEntry entry) {
        AgentPersonalityProfile profile = entry.capabilityStates().find(AgentPersonalityState.STATE_KEY)
                .map(AgentPersonalityState::profile).orElse(null);
        return profile == null ? 50 : profile.traits().sociability();
    }

}
