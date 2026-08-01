package server.agents.progression.events;

import constants.id.MapId;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Converts quest progress facts into observer-gated intention chat. */
public final class AgentQuestProgressDialogueReactionService
        implements AgentEventListener<AgentEvent> {
    public static final String INTENT_KEY = "progression.quest-progress";
    private static final long COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.progression.events.AgentProgressionDialogueReactionService.QUEST_COOLDOWN_MS");

    private final AgentEventBus bus;

    public AgentQuestProgressDialogueReactionService(AgentEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentQuestProgressMilestoneEvent progress)) {
            return;
        }
        boolean enabled = MapId.isMapleIsland(progress.mapId())
                ? config.AgentYamlConfig.config.agent.AGENT_AMHERST_INTENTION_CHAT_ENABLED
                : config.AgentYamlConfig.config.agent.AGENT_VICTORIA_INTENTION_CHAT_ENABLED;
        if (!enabled) {
            return;
        }
        bus.publish(new AgentDialogueIntentEvent(
                        progress.agentId(), progress.occurredAtMs(), INTENT_KEY,
                        AgentDialogueAudience.NEARBY_REAL_PLAYER,
                        "progression:quest-progress:" + progress.questId() + ":"
                                + progress.targetId() + ":" + progress.milestonePercent(),
                        COOLDOWN_MS,
                        Map.of(
                                "questId", String.valueOf(progress.questId()),
                                "targetId", String.valueOf(progress.targetId()),
                                "targetName", progress.targetName(),
                                "currentCount", String.valueOf(progress.currentCount()),
                                "requiredCount", String.valueOf(progress.requiredCount()),
                                "milestonePercent", String.valueOf(progress.milestonePercent()))),
                AgentEventPriority.AMBIENT);
    }
}
