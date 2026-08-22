package server.agents.runtime.activity.control;

/** Presentation-neutral eligibility used by every future Director client. */
public enum AgentDirectorActionAvailability {
    RECOMMENDED,
    AVAILABLE,
    UNAVAILABLE;

    public boolean executable() {
        return this != UNAVAILABLE;
    }
}
