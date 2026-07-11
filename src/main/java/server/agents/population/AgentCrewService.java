package server.agents.population;

import java.io.IOException;
import java.util.Collection;

public final class AgentCrewService {
    private final AgentPopulationRegistry registry;

    public AgentCrewService(AgentPopulationRegistry registry) {
        this.registry = registry;
    }

    public int assign(Integer crewId, Collection<String> agentNames) throws IOException {
        int changed = 0;
        for (String name : agentNames) {
            if (registry.setCrew(name, crewId)) {
                changed++;
            }
        }
        return changed;
    }
}
