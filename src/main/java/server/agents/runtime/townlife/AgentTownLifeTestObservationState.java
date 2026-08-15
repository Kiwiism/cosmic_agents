package server.agents.runtime.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Opt-in presentation/measurement state owned by TownLife test callers. */
public final class AgentTownLifeTestObservationState {
    public static final AgentCapabilityStateKey<AgentTownLifeTestObservationState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.test-observation",
                    AgentTownLifeTestObservationState.class,
                    AgentTownLifeTestObservationState::new);

    private boolean enabled;
    private String scenarioId = "";
    private int announcements;
    private boolean autoDisableOnExit;

    public synchronized void enable(String nextScenarioId) {
        enable(nextScenarioId, false);
    }

    public synchronized void enable(String nextScenarioId, boolean disableOnExit) {
        enabled = true;
        scenarioId = nextScenarioId == null ? "" : nextScenarioId.trim();
        announcements = 0;
        autoDisableOnExit = disableOnExit;
    }

    public synchronized void disable() {
        enabled = false;
        scenarioId = "";
        autoDisableOnExit = false;
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized String scenarioId() {
        return scenarioId;
    }

    public synchronized int announcements() {
        return announcements;
    }

    public synchronized void recordAnnouncement() {
        announcements++;
    }

    public synchronized boolean autoDisableOnExit() {
        return autoDisableOnExit;
    }
}
