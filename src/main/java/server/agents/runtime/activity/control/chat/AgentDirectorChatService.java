package server.agents.runtime.activity.control.chat;

import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposal;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalService;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalSource;

import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Natural-language explanation/proposal boundary. It cannot execute directives. */
public final class AgentDirectorChatService {
    private final AgentDirectorProposalProvider model;
    private final AgentDirectorProposalService proposals;
    private final AgentDirectorDomainContextBuilder domainContext;

    public AgentDirectorChatService(
            AgentDirectorProposalProvider model,
            AgentDirectorProposalService proposals) {
        this(model, proposals, new AgentDirectorDomainContextBuilder());
    }

    AgentDirectorChatService(
            AgentDirectorProposalProvider model,
            AgentDirectorProposalService proposals,
            AgentDirectorDomainContextBuilder domainContext) {
        if (proposals == null) throw new IllegalArgumentException("proposal service is required");
        if (domainContext == null) throw new IllegalArgumentException("domain context is required");
        this.model = model;
        this.proposals = proposals;
        this.domainContext = domainContext;
    }

    public AgentDirectorChatResult respond(
            AgentDirectorExecutiveView view, String operatorPrompt, long nowMs) {
        String prompt = operatorPrompt == null ? "" : operatorPrompt.trim();
        if (prompt.isEmpty()) throw new IllegalArgumentException("Director prompt is required");
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (domainContext.isTrainingMapQuestion(prompt)) {
            return trainingMapAdvice(view, prompt);
        }
        if (normalized.startsWith("why") || normalized.contains("status")
                || normalized.contains("current activity")
                || normalized.contains("doing now")
                || normalized.contains("energy")) {
            AgentDirectorAction recommended = view.actions().stream()
                    .filter(action -> action.availability().name().equals("RECOMMENDED"))
                    .findFirst().orElse(null);
            String reply = view.context().agentName() + " is " + view.activity().now()
                    + " with " + view.energy().energyPercent() + "% energy. "
                    + (recommended == null ? "No action is currently recommended."
                    : "The current recommendation is “" + recommended.label() + "” because "
                    + recommended.reason() + '.');
            return new AgentDirectorChatResult(
                    reply, null, List.of(), "policy-explainer", 0L);
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
                    proposal, List.of(), choice.provider(), choice.latencyMs());
        }
        AgentDirectorProposal fallback = keywordProposal(view, normalized, nowMs);
        return new AgentDirectorChatResult(
                "The model did not return a valid selection, so I used the predefined policy: “"
                        + fallback.label() + "”.",
                fallback, List.of(), "deterministic-policy", 0L);
    }

    private AgentDirectorChatResult trainingMapAdvice(
            AgentDirectorExecutiveView view, String prompt) {
        AgentDirectorDomainContext context = domainContext.build(view, prompt);
        if (context.trainingMaps().isEmpty()) {
            return new AgentDirectorChatResult(
                    "The connected Victoria training catalog currently covers levels 15–30, "
                            + "so I do not have grounded map recommendations for level "
                            + context.requestedLevel() + '.',
                    null, List.of(), "catalog-boundary", 0L);
        }
        Optional<AgentDirectorModelAdvice> advice = model == null
                ? Optional.empty() : model.recommendTrainingMaps(view, prompt, context);
        List<AgentDirectorRankedSelection> ranked = advice
                .map(AgentDirectorModelAdvice::selections).orElse(List.of());
        List<AgentDirectorChatRecommendation> recommendations =
                recommendations(context, ranked);
        boolean modelUsed = advice.isPresent() && !ranked.isEmpty();
        String prefix = modelUsed
                ? "Using the Cosmic MapleStory v83 training catalog, I evaluated "
                : "Ollama did not return a valid ranking, so I used the catalog's predefined order across ";
        String reply = prefix + context.trainingMaps().size() + " eligible level "
                + context.requestedLevel() + " maps. The top " + recommendations.size()
                + " are shown below; selecting one creates an approval-gated proposal."
                + (recommendations.stream().noneMatch(AgentDirectorChatRecommendation::selectable)
                ? " These choices are informational until the selected Agent and Director mode make "
                + "the matching hunting actions executable." : "");
        return new AgentDirectorChatResult(reply, null, recommendations,
                advice.map(AgentDirectorModelAdvice::provider)
                        .orElse("deterministic-training-catalog"),
                advice.map(AgentDirectorModelAdvice::latencyMs).orElse(0L));
    }

    private static List<AgentDirectorChatRecommendation> recommendations(
            AgentDirectorDomainContext context,
            List<AgentDirectorRankedSelection> modelSelections) {
        List<AgentDirectorRankedSelection> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AgentDirectorRankedSelection selection : modelSelections) {
            boolean known = context.trainingMaps().stream()
                    .anyMatch(candidate -> candidate.actionId().equals(selection.actionId()));
            if (known && seen.add(selection.actionId())) ordered.add(selection);
            if (ordered.size() >= context.requestedCount()) break;
        }
        for (AgentDirectorDomainContext.TrainingMapCandidate candidate : context.trainingMaps()) {
            if (ordered.size() >= context.requestedCount()) break;
            if (seen.add(candidate.actionId())) {
                ordered.add(new AgentDirectorRankedSelection(
                        candidate.actionId(), candidate.catalogRationale()));
            }
        }
        List<AgentDirectorChatRecommendation> result = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            AgentDirectorRankedSelection selection = ordered.get(index);
            AgentDirectorDomainContext.TrainingMapCandidate candidate =
                    context.trainingMaps().stream()
                            .filter(value -> value.actionId().equals(selection.actionId()))
                            .findFirst().orElseThrow();
            result.add(new AgentDirectorChatRecommendation(
                    index + 1, candidate.actionId(), candidate.label(), selection.rationale(),
                    candidate.mapId(), candidate.mapName(), candidate.catalogRank(),
                    candidate.catalogWeight(), candidate.recommendedMinLevel(),
                    candidate.recommendedMaxLevel(), candidate.terrain(), candidate.tags(),
                    candidate.hazards(), candidate.spawns(), candidate.selectable()));
        }
        return List.copyOf(result);
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
