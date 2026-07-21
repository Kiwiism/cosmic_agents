package server.agents.progression.events;

import client.QuestStatus;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Converts major progression milestones into optional observer-facing dialogue. */
public final class AgentProgressionDialogueReactionService implements AgentEventListener<AgentEvent> {
    public static final String LEVEL_INTENT = "progression.level";
    public static final String JOB_INTENT = "progression.job";
    public static final String QUEST_INTENT = "progression.quest-complete";
    private static final long LEVEL_COOLDOWN_MS = 15_000L;
    private static final long JOB_COOLDOWN_MS = 30_000L;
    private static final long QUEST_COOLDOWN_MS = 10_000L;

    private final AgentEventBus bus;

    public AgentProgressionDialogueReactionService(AgentEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentDialogueIntentEvent intent = switch (event) {
            case AgentLevelChangedEvent level -> new AgentDialogueIntentEvent(
                    level.agentId(), level.occurredAtMs(), LEVEL_INTENT,
                    AgentDialogueAudience.NEARBY_REAL_PLAYER, "progression:level",
                    LEVEL_COOLDOWN_MS, Map.of("level", String.valueOf(level.level())));
            case AgentJobAdvancedEvent job -> new AgentDialogueIntentEvent(
                    job.agentId(), job.occurredAtMs(), JOB_INTENT,
                    AgentDialogueAudience.NEARBY_REAL_PLAYER, "progression:job",
                    JOB_COOLDOWN_MS, Map.of("jobId", String.valueOf(job.jobId())));
            case AgentQuestStateChangedEvent quest
                    when quest.status() == QuestStatus.Status.COMPLETED.getId() ->
                    new AgentDialogueIntentEvent(
                            quest.agentId(), quest.occurredAtMs(), QUEST_INTENT,
                            AgentDialogueAudience.NEARBY_REAL_PLAYER,
                            "progression:quest:" + quest.questId(), QUEST_COOLDOWN_MS,
                            Map.of("questId", String.valueOf(quest.questId())));
            default -> null;
        };
        if (intent != null) {
            bus.publish(intent, AgentEventPriority.AMBIENT);
        }
    }
}
