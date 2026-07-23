package server.agents.runtime;

/**
 * Multi-owner suspension clock for the current foreground activity.
 *
 * <p>Capabilities pause by stable reason without knowing which plan or
 * objective executor owns the foreground work. Executors consume effective
 * time so timeouts do not advance while any reason remains active.</p>
 */
public final class AgentForegroundPauseRuntime {
    private AgentForegroundPauseRuntime() {
    }

    public static void pause(AgentRuntimeEntry entry, String reason, long nowMs) {
        if (entry != null) {
            entry.capabilityStates().require(AgentForegroundPauseState.STATE_KEY)
                    .pause(reason, nowMs);
        }
    }

    public static void resume(AgentRuntimeEntry entry, String reason, long nowMs) {
        if (entry != null) {
            entry.capabilityStates().require(AgentForegroundPauseState.STATE_KEY)
                    .resume(reason, nowMs);
        }
    }

    public static boolean paused(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentForegroundPauseState.STATE_KEY)
                .map(AgentForegroundPauseState::paused).orElse(false);
    }

    public static long effectiveNow(AgentRuntimeEntry entry, long wallNowMs) {
        return entry == null
                ? wallNowMs
                : entry.capabilityStates().require(AgentForegroundPauseState.STATE_KEY)
                .effectiveNow(wallNowMs);
    }

    public static void reset(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.capabilityStates().remove(AgentForegroundPauseState.STATE_KEY);
        }
    }
}
