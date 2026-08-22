package server.agents.runtime.activity.control;

/** Stable activity text model; clients decide only how to render it. */
public record AgentDirectorActivityProjection(
        String now,
        String next,
        String waitingOn,
        String blockedBy,
        String retained,
        String lastEvent) {
    public AgentDirectorActivityProjection {
        now = text(now);
        next = text(next);
        waitingOn = text(waitingOn);
        blockedBy = text(blockedBy);
        retained = text(retained);
        lastEvent = text(lastEvent);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
