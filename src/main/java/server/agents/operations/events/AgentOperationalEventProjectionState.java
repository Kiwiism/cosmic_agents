package server.agents.operations.events;

import server.agents.events.AgentEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded per-session operational read model for diagnostics and policy inputs. */
public final class AgentOperationalEventProjectionState {
    public static final AgentCapabilityStateKey<AgentOperationalEventProjectionState> STATE_KEY =
            new AgentCapabilityStateKey<>("operations.event-projection",
                    AgentOperationalEventProjectionState.class,
                    AgentOperationalEventProjectionState::new);

    private long targetTransitions;
    private long kills;
    private long routeFailures;
    private long mapTransitions;
    private long stuckDetections;
    private long recoveries;
    private long lifeTransitions;
    private long revision;
    private AgentEvent lastEvent;

    public synchronized void record(AgentEvent event) {
        if (event instanceof AgentCombatTargetChangedEvent) {
            targetTransitions++;
        } else if (event instanceof AgentMobKilledEvent) {
            kills++;
        } else if (event instanceof AgentNavigationRouteFailedEvent) {
            routeFailures++;
        } else if (event instanceof AgentMapTransitionedEvent) {
            mapTransitions++;
        } else if (event instanceof AgentStuckDetectedEvent) {
            stuckDetections++;
        } else if (event instanceof AgentRecoveryPerformedEvent) {
            recoveries++;
        } else if (event instanceof AgentLifeStateChangedEvent) {
            lifeTransitions++;
        } else {
            return;
        }
        revision++;
        lastEvent = event;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(targetTransitions, kills, routeFailures, mapTransitions,
                stuckDetections, recoveries, lifeTransitions, revision, lastEvent);
    }

    public record Snapshot(long targetTransitions,
                           long kills,
                           long routeFailures,
                           long mapTransitions,
                           long stuckDetections,
                           long recoveries,
                           long lifeTransitions,
                           long revision,
                           AgentEvent lastEvent) {
    }
}
