package server.agents.plans.mapleisland.dialogue;

import server.agents.capabilities.dialogue.conversation.AgentConversationActivity;
import server.agents.capabilities.dialogue.conversation.AgentConversationActivityProvider;
import server.agents.plans.amherst.AmherstObjectiveProgress;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanExecutionState;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;
import server.agents.runtime.AgentRuntimeEntry;

/** Adapts Maple Island plan progress to region-neutral conversation activity facts. */
public final class MapleIslandConversationActivityProvider
        implements AgentConversationActivityProvider {
    private static final long RECENT_COMPLETION_WINDOW_MS = 20_000L;

    @Override
    public AgentConversationActivity snapshot(AgentRuntimeEntry entry, long nowMs) {
        AmherstPlanExecutionState state = entry == null ? null : entry.amherstPlanExecutionState();
        if (state == null) {
            return AgentConversationActivity.NONE;
        }
        AmherstPlanProgressSnapshot progress = state.progress();
        String objectiveId = state.assignedObjectiveId();
        AmherstObjectiveProgress objective = progress == null || objectiveId == null
                ? null : progress.objectives().get(objectiveId);
        long objectiveAgeMs = objective == null || objective.startedAtMs() <= 0L
                ? 0L : Math.max(0L, nowMs - objective.startedAtMs());
        boolean recentlyCompleted = progress != null && progress.objectives().values().stream()
                .filter(value -> value.status() == AmherstObjectiveProgressStatus.SATISFIED)
                .mapToLong(AmherstObjectiveProgress::completedAtMs)
                .anyMatch(completedAt -> completedAt > 0L
                        && nowMs - completedAt <= RECENT_COMPLETION_WINDOW_MS);
        return new AgentConversationActivity(
                state.active(),
                objectiveId != null && objectiveId.contains("kill-mobs"),
                objectiveAgeMs,
                recentlyCompleted,
                objectiveId);
    }
}
