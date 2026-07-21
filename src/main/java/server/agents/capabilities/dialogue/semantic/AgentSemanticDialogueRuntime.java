package server.agents.capabilities.dialogue.semantic;

import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEventPriority;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.HashMap;
import java.util.Map;

/** Shared semantic-act to observer-gated presentation boundary. */
public final class AgentSemanticDialogueRuntime {
    public static final String SEMANTIC_INTENT_KEY = "dialogue.semantic";
    private static final AgentDialogueRealizer realizer = new AgentDeterministicDialogueRealizer();

    private AgentSemanticDialogueRuntime() {
    }

    public static boolean emit(AgentRuntimeEntry entry, AgentSemanticDialogueAct act) {
        if (entry == null || act == null || !AgentDialogueTopicRegistry.enabled(act.topicId())) {
            AgentDialogueMetrics.recordTopicSuppressed();
            return false;
        }
        try {
            AgentPersonalityProfile profile = entry.capabilityStates().find(AgentPersonalityState.STATE_KEY)
                    .map(AgentPersonalityState::profile)
                    .orElse(null);
            String text = realizer.realize(act, profile);
            if (text.isBlank()) {
                return false;
            }
            Map<String, String> parameters = new HashMap<>(act.parameters());
            parameters.put("text", text);
            parameters.put("topicId", act.topicId());
            if (act.listenerAgentId() > 0) {
                parameters.put("listenerAgentId", String.valueOf(act.listenerAgentId()));
            }
            AgentDialogueMetrics.recordSemanticAct();
            return AgentSessionEventRuntime.bus(entry).publish(new AgentDialogueIntentEvent(
                    act.speakerAgentId(), act.occurredAtMs(), SEMANTIC_INTENT_KEY,
                    act.audience(), act.dedupeKey(), act.cooldownMs(), parameters),
                    AgentEventPriority.NORMAL);
        } catch (RuntimeException ignored) {
            // Dialogue is a presentation layer and must never alter objective execution.
            AgentDialogueMetrics.recordFailure();
            return false;
        }
    }
}
