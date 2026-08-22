package server.agents.runtime.activity.control.proposal;

import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorActionAvailability;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.AgentWorldDirectorApplication;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Approval boundary shared by policy, model, and operator proposal sources. */
public final class AgentDirectorProposalService {
    private static final long PROPOSAL_TTL_MS = 5 * 60_000L;
    private final AgentDirectorProposalStore store;

    public AgentDirectorProposalService(AgentDirectorProposalStore store) {
        if (store == null) throw new IllegalArgumentException("proposal store is required");
        this.store = store;
    }

    public List<AgentDirectorProposal> list(int agentId, long nowMs) {
        return store.list(agentId).stream().map(proposal -> expire(proposal, nowMs)).toList();
    }

    public AgentDirectorProposal propose(
            AgentDirectorExecutiveView view,
            String actionId,
            AgentDirectorProposalSource source,
            String rationale,
            int expectedEnergyDelta,
            long nowMs) {
        AgentDirectorAction action = view.actions().stream()
                .filter(candidate -> candidate.actionId().equals(text(actionId)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown Director action"));
        if (!action.availability().executable()) {
            throw new IllegalStateException(action.reason());
        }
        List<String> alternatives = view.actions().stream()
                .filter(candidate -> candidate.availability().executable())
                .filter(candidate -> !candidate.actionId().equals(action.actionId()))
                .limit(4).map(AgentDirectorAction::actionId).toList();
        var evidence = new LinkedHashMap<String, String>();
        evidence.put("actionReason", action.reason());
        evidence.put("availability", action.availability().name());
        evidence.put("energyBand", view.energy().band());
        evidence.put("energyPercent", Integer.toString(view.energy().energyPercent()));
        evidence.put("activity", view.activity().now());
        AgentDirectorProposal proposal = new AgentDirectorProposal(
                1, UUID.randomUUID().toString(), view.context().agentId(), source,
                view.contextRevision(), action.actionId(), action.label(),
                text(rationale).isEmpty() ? action.reason() : text(rationale),
                evidence, alternatives, Math.max(-100, Math.min(100, expectedEnergyDelta)),
                nowMs, nowMs + PROPOSAL_TTL_MS, AgentDirectorProposalStatus.PENDING,
                0L, "", "");
        return store.save(proposal);
    }

    public AgentDirectorProposal proposeRecommended(
            AgentDirectorExecutiveView view, AgentDirectorProposalSource source, long nowMs) {
        AgentDirectorAction action = view.actions().stream()
                .filter(candidate -> candidate.availability()
                        == AgentDirectorActionAvailability.RECOMMENDED)
                .findFirst().orElseGet(() -> view.actions().stream()
                        .filter(candidate -> candidate.availability().executable())
                        .findFirst().orElseThrow(() ->
                                new IllegalStateException("no executable Director action is available")));
        return propose(view, action.actionId(), source, action.reason(), 0, nowMs);
    }

    public ApprovalResult approve(
            AgentWorldDirectorApplication application,
            int agentId,
            String proposalId,
            boolean confirmDestructive,
            long nowMs) {
        AgentDirectorProposal proposal = required(agentId, proposalId);
        proposal = expire(proposal, nowMs);
        if (proposal.status() != AgentDirectorProposalStatus.PENDING) {
            throw new IllegalStateException("proposal is no longer pending");
        }
        AgentDirectorExecutiveView view = application.view(agentId, 12, nowMs);
        if (!view.contextRevision().equals(proposal.contextRevision())) {
            AgentDirectorProposal stale = store.save(proposal.resolve(
                    AgentDirectorProposalStatus.STALE,
                    "Agent context changed; refresh and create a new proposal", "", nowMs));
            throw new StaleProposalException(stale);
        }
        String directiveId = "proposal:" + proposal.proposalId();
        AgentWorldDirectiveEnvelope directive = application.execute(
                agentId, proposal.actionId(), proposal.contextRevision(), directiveId,
                proposal.rationale(), confirmDestructive, nowMs);
        AgentDirectorProposal executed = store.save(proposal.resolve(
                AgentDirectorProposalStatus.EXECUTED,
                "approved and submitted to Agent OS", directiveId, nowMs));
        return new ApprovalResult(executed, directive);
    }

    public AgentDirectorProposal reject(
            int agentId, String proposalId, String reason, long nowMs) {
        AgentDirectorProposal proposal = expire(required(agentId, proposalId), nowMs);
        if (proposal.status() != AgentDirectorProposalStatus.PENDING) return proposal;
        return store.save(proposal.resolve(AgentDirectorProposalStatus.REJECTED,
                text(reason).isEmpty() ? "rejected by operator" : text(reason), "", nowMs));
    }

    private AgentDirectorProposal expire(AgentDirectorProposal proposal, long nowMs) {
        if (!proposal.expiredAt(nowMs)) return proposal;
        return store.save(proposal.resolve(AgentDirectorProposalStatus.STALE,
                "proposal expired before approval", "", nowMs));
    }

    private AgentDirectorProposal required(int agentId, String proposalId) {
        return store.load(agentId, proposalId)
                .orElseThrow(() -> new IllegalStateException("unknown Director proposal"));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record ApprovalResult(
            AgentDirectorProposal proposal,
            AgentWorldDirectiveEnvelope directive) { }

    public static final class StaleProposalException extends IllegalStateException {
        private final AgentDirectorProposal proposal;
        public StaleProposalException(AgentDirectorProposal proposal) {
            super(proposal.resolution());
            this.proposal = proposal;
        }
        public AgentDirectorProposal proposal() { return proposal; }
    }
}
