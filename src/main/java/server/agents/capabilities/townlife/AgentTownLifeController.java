package server.agents.capabilities.townlife;

import java.util.Optional;

/** Optional policy plugin seam. Execution always remains inside the TownLife runtime. */
@FunctionalInterface
public interface AgentTownLifeController {
    Optional<AgentTownLifeDirective> propose(AgentTownLifeDecisionContext context);
}
