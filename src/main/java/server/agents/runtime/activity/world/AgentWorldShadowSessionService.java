package server.agents.runtime.activity.world;

import java.util.List;
import java.util.Optional;

/** Command-driven shadow persistence. It deliberately exposes no live-control operation. */
public final class AgentWorldShadowSessionService {
    private final AgentWorldDirectorSessionStore sessions;
    private final AgentWorldDirectorJournalStore journal;
    private final AgentWorldShadowEvaluator evaluator;

    public AgentWorldShadowSessionService(
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectorJournalStore journal,
            AgentWorldShadowEvaluator evaluator) {
        if (sessions == null || journal == null || evaluator == null) {
            throw new IllegalArgumentException("shadow stores and evaluator are required");
        }
        this.sessions = sessions;
        this.journal = journal;
        this.evaluator = evaluator;
    }

    public static AgentWorldShadowSessionService runtimeDefault() {
        return new AgentWorldShadowSessionService(
                AgentFileWorldDirectorSessionStore.runtimeDefault(),
                AgentFileWorldDirectorJournalStore.runtimeDefault(),
                AgentWorldShadowEvaluator.baseline());
    }

    public AgentWorldShadowReport inspect(AgentWorldContext context) {
        return evaluator.evaluate(context);
    }

    public AgentWorldShadowReport start(AgentWorldContext context) {
        sessions.save(AgentWorldDirectorSession.shadow(
                context.agentId(), context.capturedAtMs()));
        return sample(context);
    }

    public AgentWorldShadowReport sample(AgentWorldContext context) {
        AgentWorldDirectorSession session = sessions.load(context.agentId())
                .orElseThrow(() -> new IllegalStateException("shadow observation is not enabled"));
        AgentWorldShadowReport report = evaluator.evaluate(context);
        sessions.save(session.observe(report.decision(), context, context.capturedAtMs()));
        journal.append(report.journalEntry());
        return report;
    }

    public Optional<AgentWorldDirectorSession> session(int agentId) {
        return sessions.load(agentId);
    }

    public List<AgentWorldDirectorJournalEntry> recent(int agentId, int limit) {
        return journal.recent(agentId, limit);
    }

    public AgentWorldDirectorSession stop(int agentId, String reason, long nowMs) {
        AgentWorldDirectorSession current = sessions.load(agentId)
                .orElseThrow(() -> new IllegalStateException("shadow observation is not enabled"));
        AgentWorldDirectorSession paused = current.pause(reason, nowMs);
        sessions.save(paused);
        return paused;
    }
}
