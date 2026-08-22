package server.agents.runtime.activity.outcome;

import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.util.List;
import java.util.Optional;

public interface AgentActivityOutcomeInbox {
    AgentActivityOutcomeEnvelope publish(
            String outcomeId, AgentActivityTerminalOutcome outcome, long nowMs);

    Optional<AgentActivityOutcomeEnvelope> load(String outcomeId);

    List<AgentActivityOutcomeEnvelope> pending(String agentId);

    AgentActivityOutcomeEnvelope acknowledge(String outcomeId, String reason, long nowMs);
}
