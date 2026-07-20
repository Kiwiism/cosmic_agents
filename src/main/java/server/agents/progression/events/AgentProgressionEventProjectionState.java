package server.agents.progression.events;

import server.agents.events.AgentEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded per-session progression read model for diagnostics and decision inputs. */
public final class AgentProgressionEventProjectionState {
    public static final AgentCapabilityStateKey<AgentProgressionEventProjectionState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.event-projection",
                    AgentProgressionEventProjectionState.class,
                    AgentProgressionEventProjectionState::new);

    private long levelTransitions;
    private long jobAdvancements;
    private long apAssigned;
    private long spAssigned;
    private long questTransitions;
    private long checkpoints;
    private long revision;
    private AgentEvent lastEvent;

    public synchronized void record(AgentEvent event) {
        if (event instanceof AgentLevelChangedEvent) {
            levelTransitions++;
        } else if (event instanceof AgentJobAdvancedEvent) {
            jobAdvancements++;
        } else if (event instanceof AgentApAssignedEvent ap) {
            apAssigned += ap.str() + ap.dex() + ap.intelligence() + ap.luk();
        } else if (event instanceof AgentSkillLearnedEvent skill) {
            spAssigned += skill.skillLevel() - skill.previousSkillLevel();
        } else if (event instanceof AgentQuestStateChangedEvent) {
            questTransitions++;
        } else if (event instanceof AgentProgressionCheckpointEvent) {
            checkpoints++;
        } else {
            return;
        }
        revision++;
        lastEvent = event;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(levelTransitions, jobAdvancements, apAssigned, spAssigned,
                questTransitions, checkpoints, revision, lastEvent);
    }

    public record Snapshot(long levelTransitions,
                           long jobAdvancements,
                           long apAssigned,
                           long spAssigned,
                           long questTransitions,
                           long checkpoints,
                           long revision,
                           AgentEvent lastEvent) {
    }
}
