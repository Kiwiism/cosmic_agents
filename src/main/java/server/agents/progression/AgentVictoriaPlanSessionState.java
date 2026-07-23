package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

/**
 * Session-local ownership of a Victoria progression plan.
 *
 * <p>This state is intentionally not persisted. A restored career checkpoint describes where an
 * Agent may resume, but only an explicit test session or progression handoff may activate it.</p>
 */
public final class AgentVictoriaPlanSessionState {
    public enum Plan {
        NONE,
        FIRST_JOB,
        TRAINING
    }

    public static final AgentCapabilityStateKey<AgentVictoriaPlanSessionState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.victoria-session",
                    AgentVictoriaPlanSessionState.class, AgentVictoriaPlanSessionState::new);

    private Plan plan = Plan.NONE;

    public synchronized void start(Plan plan) {
        if (plan == null || plan == Plan.NONE) {
            throw new IllegalArgumentException("An active Victoria plan is required");
        }
        this.plan = plan;
    }

    public synchronized void stop() {
        plan = Plan.NONE;
    }

    public synchronized boolean active() {
        return plan != Plan.NONE;
    }

    public synchronized Plan plan() {
        return plan;
    }
}
