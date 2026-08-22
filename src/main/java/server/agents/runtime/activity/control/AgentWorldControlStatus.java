package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

import java.util.List;

public record AgentWorldControlStatus(
        AgentWorldDirectorSession session,
        List<AgentWorldDirectiveEnvelope> directives) {
    public AgentWorldControlStatus {
        if (session == null) throw new IllegalArgumentException("Director session is required");
        directives = List.copyOf(directives == null ? List.of() : directives);
    }
}
