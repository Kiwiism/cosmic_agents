package server.agents.runtime.activity.outcome;

import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.decision.AgentDecisionRecommendation;

import java.util.ArrayList;
import java.util.List;

/** Publishes child terminal states once and records fallback advice before acknowledgement. */
public final class AgentActivityOutcomeProcessor {
    private final AgentActivityOutcomeInbox inbox;
    private final AgentActivityOutcomeRecoveryPolicy recovery;
    private final AgentActivityOutcomeRecommendationSink recommendations;

    public AgentActivityOutcomeProcessor(
            AgentActivityOutcomeInbox inbox,
            AgentActivityOutcomeRecoveryPolicy recovery,
            AgentActivityOutcomeRecommendationSink recommendations) {
        if (inbox == null || recovery == null || recommendations == null) {
            throw new IllegalArgumentException("complete activity outcome dependencies are required");
        }
        this.inbox = inbox;
        this.recovery = recovery;
        this.recommendations = recommendations;
    }

    public List<AgentActivityOutcomeEnvelope> publish(
            List<AgentLiveActivityFacade> activities, long nowMs) {
        if (activities == null || nowMs < 0L) {
            throw new IllegalArgumentException("activities and current time are required");
        }
        List<AgentActivityOutcomeEnvelope> published = new ArrayList<>();
        for (AgentLiveActivityFacade activity : activities) {
            if (activity == null) continue;
            AgentActivityTerminalOutcome outcome = activity.outcome().terminalOutcome(nowMs);
            if (outcome == null) continue;
            String outcomeId = outcome.kind().name().toLowerCase() + ':'
                    + outcome.sessionId() + ':' + outcome.phase().name().toLowerCase();
            published.add(inbox.publish(outcomeId, outcome, nowMs));
        }
        return List.copyOf(published);
    }

    public AgentDecisionRecommendation recommendNext(String agentId, long nowMs) {
        AgentActivityOutcomeEnvelope envelope = inbox.pending(agentId).stream().findFirst()
                .orElse(null);
        if (envelope == null) return null;
        AgentDecisionRecommendation recommendation = recovery.recommend(
                envelope.outcomeId(), envelope.outcome(), nowMs);
        recommendations.record(recommendation);
        inbox.acknowledge(envelope.outcomeId(),
                "recovery recommendation recorded: " + recommendation.action(), nowMs);
        return recommendation;
    }
}
