package server.agents.capabilities.townlife;

import java.util.List;

/** Immutable population boundary used by TownLife policy. */
public interface AgentTownLifePopulationPort {
    List<AgentView> activeTownAgents();

    record AgentView(int agentId,
                     int world,
                     int channel,
                     int mapId,
                     String venueId) {
        public AgentView {
            venueId = venueId == null ? "" : venueId;
        }
    }
}
