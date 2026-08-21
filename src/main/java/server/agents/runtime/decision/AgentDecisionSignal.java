package server.agents.runtime.decision;

/** One immutable observation supplied to an advisory policy. */
public record AgentDecisionSignal(
        AgentDecisionSignalKind kind,
        long observedAtMs,
        String source,
        String subject,
        long value,
        String unit,
        String detail) {

    public AgentDecisionSignal {
        source = text(source);
        subject = text(subject);
        unit = text(unit);
        detail = text(detail);
        if (kind == null || observedAtMs < 0L || source.isEmpty()) {
            throw new IllegalArgumentException("decision signal kind, timing, and source are required");
        }
    }

    public static AgentDecisionSignal observed(
            AgentDecisionSignalKind kind,
            long observedAtMs,
            String source,
            String subject,
            String detail) {
        return new AgentDecisionSignal(kind, observedAtMs, source, subject, 0L, "", detail);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
