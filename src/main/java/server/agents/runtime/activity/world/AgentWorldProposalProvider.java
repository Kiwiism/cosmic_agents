package server.agents.runtime.activity.world;

import java.util.List;

/** Read-only proposal source. Providers cannot admit or execute an activity. */
@FunctionalInterface
public interface AgentWorldProposalProvider {
    List<AgentWorldActivityIntent> propose(
            AgentWorldContext context, AgentWorldMilestoneSnapshot milestones);
}
