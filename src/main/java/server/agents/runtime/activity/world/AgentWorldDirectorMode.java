package server.agents.runtime.activity.world;

/**
 * Per-Agent World Director authority. Live modes still require the independent
 * rollout gate before they may own an activity.
 */
public enum AgentWorldDirectorMode {
    DISABLED,
    /** Legacy name retained so persisted preparation sessions remain readable. */
    SHADOW,
    OBSERVE,
    MANUAL,
    ASSISTED,
    /** Legacy name retained until controlled sessions have been migrated to MANUAL. */
    @Deprecated
    CONTROLLED,
    AUTONOMOUS,
    EMERGENCY_HOLD;

    public boolean isObservationOnly() {
        return this == SHADOW || this == OBSERVE;
    }

    public boolean acceptsOperatorDirectives() {
        return this == MANUAL || this == CONTROLLED || this == ASSISTED
                || this == AUTONOMOUS;
    }

    public boolean allowsAutomaticProposals() {
        return this == ASSISTED || this == AUTONOMOUS;
    }
}
