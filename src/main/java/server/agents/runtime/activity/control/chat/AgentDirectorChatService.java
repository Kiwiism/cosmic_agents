package server.agents.runtime.activity.control.chat;

import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposal;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalService;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalSource;

import java.util.Locale;
import java.util.Optional;

/** Natural-language explanation/proposal boundary. It cannot execute directives. */
public final class AgentDirectorChatService {
    private final AgentDirectorProposalProvider model;
    private final AgentDirectorProposalService proposals;

    public AgentDirectorChatService(
            AgentDirectorProposalProvider model,
            AgentDirectorProposalService proposals) {
        if (proposals == null) throw new IllegalArgumentException("proposal service is required");
        this.model = model;
        this.proposals = proposals;
    }

    public AgentDirectorChatResult respond(
            AgentDirectorExecutiveView view, String operatorPrompt, long nowMs) {
        String prompt = operatorPrompt == null ? "" : operatorPrompt.trim();
        if (prompt.isEmpty()) throw new IllegalArgumentException("Director prompt is required");
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("why") || normalized.contains("status")
                || normalized.startsWith("what")) {
            AgentDirectorAction recommended = view.actions().stream()
                    .filter(action -> action.availability().name().equals("RECOMMENDED"))
                    .findFirst().orElse(null);
            String reply = view.context().agentName() + " is " + view.activity().now()
                    + " with " + view.energy().energyPercent() + "% energy. "
                    + (recommended == null ? "No action is currently recommended."
                    : "The current recommendation is “" + recommended.label() + "” because "
                    + recommended.reason() + '.');
            return new AgentDirectorChatResult(reply, null, "policy-explainer", 0L);
        }
        Optional<AgentDirectorModelSelection> selected = model == null
                ? Optional.empty() : model.select(view, prompt);
        if (selected.isPresent()) {
            AgentDirectorModelSelection choice = selected.orElseThrow();
            AgentDirectorProposal proposal = proposals.propose(
                    view, choice.actionId(), AgentDirectorProposalSource.LLM,
                    choice.rationale(), choice.expectedEnergyDelta(), nowMs);
            return new AgentDirectorChatResult(
                    "I propose “" + proposal.label() + "”. Review the evidence before approving it.",
                    proposal, choice.provider(), choice.latencyMs());
        }
        AgentDirectorProposal fallback = keywordProposal(view, normalized, nowMs);
        return new AgentDirectorChatResult(
                "The model did not return a valid selection, so I used the predefined policy: “"
                        + fallback.label() + "”.",
                fallback, "deterministic-policy", 0L);
    }

    private AgentDirectorProposal keywordProposal(
            AgentDirectorExecutiveView view, String prompt, long nowMs) {
        String keyword = prompt.contains("supply") || prompt.contains("potion") ? "resupply"
                : prompt.contains("market") || prompt.contains("commerce") ? "commerce"
                : prompt.contains("town") || prompt.contains("rest") ? "town-life"
                : prompt.contains("hunt") || prompt.contains("grind") ? "hunting"
                : prompt.contains("quest") ? "quest"
                : prompt.contains("resume") ? "resume"
                : prompt.contains("stop") ? "stop" : "";
        AgentDirectorAction match = keyword.isEmpty() ? null : view.actions().stream()
                .filter(action -> action.availability().executable())
                .filter(action -> (action.actionId() + ' ' + action.label())
                        .toLowerCase(Locale.ROOT).contains(keyword))
                .findFirst().orElse(null);
        if (match != null) {
            return proposals.propose(view, match.actionId(), AgentDirectorProposalSource.POLICY,
                    match.reason(), keyword.equals("town-life") ? 10 : 0, nowMs);
        }
        return proposals.proposeRecommended(view, AgentDirectorProposalSource.POLICY, nowMs);
    }
}
