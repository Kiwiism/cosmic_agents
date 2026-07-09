package server.agents.integration;

import java.util.List;

public record AgentPartySnapshot(int id, List<AgentPartyMemberSnapshot> members) {
}
