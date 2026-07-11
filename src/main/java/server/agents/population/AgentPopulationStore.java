package server.agents.population;

import java.io.IOException;

public interface AgentPopulationStore {
    AgentPopulationSnapshot load() throws IOException;

    void save(AgentPopulationSnapshot snapshot) throws IOException;
}
