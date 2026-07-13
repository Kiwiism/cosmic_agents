package server.agents.runtime.scheduler;

public enum AgentPriorityClass {
    CRITICAL,
    INTERACTIVE,
    VISIBLE,
    BACKGROUND_ACTIVE,
    BACKGROUND_ABSTRACT,
    DEFERRED;

    AgentPriorityClass promoted(int levels) {
        if (this == CRITICAL || levels < 1) {
            return this;
        }
        int promotedOrdinal = Math.max(INTERACTIVE.ordinal(), ordinal() - levels);
        return values()[promotedOrdinal];
    }

    int maximumPromotionLevels() {
        return this == CRITICAL ? 0 : Math.max(0, ordinal() - INTERACTIVE.ordinal());
    }

    boolean isVisibleOrHigher() {
        return ordinal() <= VISIBLE.ordinal();
    }
}
