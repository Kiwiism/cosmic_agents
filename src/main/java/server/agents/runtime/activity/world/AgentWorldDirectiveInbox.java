package server.agents.runtime.activity.world;

import java.util.List;
import java.util.Optional;

public interface AgentWorldDirectiveInbox {
    AgentWorldDirectiveEnvelope submit(AgentWorldDirective directive, long nowMs);

    Optional<AgentWorldDirectiveEnvelope> nextPending(int agentId, long nowMs);

    AgentWorldDirectiveEnvelope claim(int agentId, String directiveId, long nowMs);

    AgentWorldDirectiveEnvelope resolve(
            int agentId,
            String directiveId,
            AgentWorldDirectiveStatus terminalStatus,
            String reason,
            long nowMs);

    Optional<AgentWorldDirectiveEnvelope> load(int agentId, String directiveId);

    List<AgentWorldDirectiveEnvelope> list(int agentId);
}
