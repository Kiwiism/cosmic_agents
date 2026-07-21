package server.agents.capabilities.dialogue;

import server.agents.capabilities.dialogue.semantic.AgentDialogueMetrics;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;

import java.util.HashMap;
import java.util.Map;

/** Audience/cooldown gate between operational events and visible Maple chat. */
public final class AgentDialogueProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentDialogueObserverView observers;
    private final AgentDialogueProjectionGateway gateway;
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    public AgentDialogueProjectionService(
            AgentDialogueObserverView observers,
            AgentDialogueProjectionGateway gateway) {
        if (observers == null || gateway == null) {
            throw new IllegalArgumentException("Dialogue observer and projection gateways are required");
        }
        this.observers = observers;
        this.gateway = gateway;
    }

    @Override
    public synchronized void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentDialogueIntentEvent intent)) {
            return;
        }
        boolean semantic = AgentSemanticDialogueRuntime.SEMANTIC_INTENT_KEY.equals(intent.intentKey());
        if (!observers.hasAudience(intent.agentId(), intent.audience())) {
            if (semantic) {
                AgentDialogueMetrics.recordNoAudienceSuppressed();
            }
            return;
        }
        String cooldownKey = intent.agentId() + ":" + intent.dedupeKey();
        if (intent.occurredAtMs() < cooldownUntil.getOrDefault(cooldownKey, 0L)) {
            if (semantic) {
                AgentDialogueMetrics.recordCooldownSuppressed();
            }
            return;
        }
        if (semantic) {
            AgentDialogueMetrics.recordProjectionRequest();
        }
        gateway.project(intent);
        cooldownUntil.put(cooldownKey, saturatedAdd(intent.occurredAtMs(), intent.cooldownMs()));
    }

    private static long saturatedAdd(long value, long increment) {
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }
}
