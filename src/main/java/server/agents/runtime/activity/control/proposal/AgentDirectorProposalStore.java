package server.agents.runtime.activity.control.proposal;

import java.util.List;
import java.util.Optional;

public interface AgentDirectorProposalStore {
    AgentDirectorProposal save(AgentDirectorProposal proposal);
    Optional<AgentDirectorProposal> load(int agentId, String proposalId);
    List<AgentDirectorProposal> list(int agentId);
}
