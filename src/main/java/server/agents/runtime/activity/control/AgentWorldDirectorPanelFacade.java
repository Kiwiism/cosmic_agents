package server.agents.runtime.activity.control;

import server.agents.runtime.activity.control.binding.AgentWorldDirectiveRequestCompiler;
import server.agents.runtime.activity.control.binding.AgentStandardWorldActivityBindingResolver;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeInbox;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.activity.world.AgentWorldActivityAdapterCatalog;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectorJournalStore;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

/** Headless manual/automatic control surface intended to back a later non-technical UI. */
public final class AgentWorldDirectorPanelFacade {
    private final AgentWorldDirectorControlService control;
    private final AgentActivityOutcomeInbox outcomes;
    private final AgentWorldDirectorJournalStore journal;
    private final AgentWorldDirectiveRequestCompiler requests;
    private final AgentWorldActivityAdapterCatalog coverage;

    public AgentWorldDirectorPanelFacade(
            AgentWorldDirectorControlService control,
            AgentActivityOutcomeInbox outcomes,
            AgentWorldDirectorJournalStore journal,
            AgentWorldDirectiveRequestCompiler requests,
            AgentWorldActivityAdapterCatalog coverage) {
        if (control == null || outcomes == null || journal == null
                || requests == null || coverage == null) {
            throw new IllegalArgumentException("complete World Director panel dependencies are required");
        }
        this.control = control;
        this.outcomes = outcomes;
        this.journal = journal;
        this.requests = requests;
        this.coverage = coverage;
    }

    public AgentWorldDirectorSession setMode(
            int agentId, AgentWorldDirectorMode mode, String reason, long nowMs) {
        return control.setMode(agentId, mode, reason, nowMs);
    }

    public AgentWorldDirectivePreview preview(AgentWorldDirective directive, long nowMs) {
        AgentWorldDirectivePreview authority = control.preview(directive, nowMs);
        if (!authority.accepted() || directive.targetActivityKind() == null) return authority;
        if (!AgentStandardWorldActivityBindingResolver.supportedTargets()
                .contains(directive.targetActivityKind())) {
            return new AgentWorldDirectivePreview(directive, authority.mode(), false,
                    "target aggregate admission is not connected to Director execution");
        }
        AgentWorldActivityAdapterCatalog.Coverage adapter =
                coverage.coverage(directive.targetActivityKind());
        if (adapter == null || !adapter.complete()) {
            return new AgentWorldDirectivePreview(directive, authority.mode(), false,
                    "target activity has no complete lifecycle adapter");
        }
        try {
            requests.compile(directive);
            return authority;
        } catch (IllegalArgumentException invalid) {
            return new AgentWorldDirectivePreview(directive, authority.mode(), false,
                    "invalid activity request: " + invalid.getMessage());
        }
    }

    public AgentWorldDirectiveEnvelope submit(AgentWorldDirective directive, long nowMs) {
        AgentWorldDirectivePreview preview = preview(directive, nowMs);
        if (!preview.accepted()) throw new IllegalStateException(preview.reason());
        return control.submit(directive, nowMs);
    }

    public AgentWorldDirectiveEnvelope cancel(
            int agentId, String directiveId, String reason, long nowMs) {
        return control.cancel(agentId, directiveId, reason, nowMs);
    }

    public AgentWorldDirectorPanelView view(int agentId, int journalLimit) {
        if (journalLimit < 1) throw new IllegalArgumentException("positive journal limit is required");
        return new AgentWorldDirectorPanelView(control.status(agentId),
                outcomes.pending(Integer.toString(agentId)), journal.recent(agentId, journalLimit),
                coverage.all());
    }

    public AgentActivityOutcomeEnvelope acknowledgeOutcome(
            String outcomeId, String reason, long nowMs) {
        return outcomes.acknowledge(outcomeId, reason, nowMs);
    }
}
