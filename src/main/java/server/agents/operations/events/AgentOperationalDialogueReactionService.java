package server.agents.operations.events;

import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Converts visible life-state transitions into observer-gated dialogue intents. */
public final class AgentOperationalDialogueReactionService implements AgentEventListener<AgentEvent> {
    public static final String LIFE_STATE_INTENT = "operations.life-state";
    private static final long COOLDOWN_MS = 5_000L;
    private final AgentEventBus bus;

    public AgentOperationalDialogueReactionService(AgentEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentLifeStateChangedEvent life) || !life.observerDialogue()) {
            return;
        }
        bus.publish(new AgentDialogueIntentEvent(
                        life.agentId(), life.occurredAtMs(), LIFE_STATE_INTENT,
                        AgentDialogueAudience.NEARBY_REAL_PLAYER,
                        "life-state:" + life.state(), COOLDOWN_MS,
                        Map.of("state", life.state())),
                AgentEventPriority.AMBIENT);
    }
}
