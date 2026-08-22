package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;

public record AgentWorldDirectivePreview(
        AgentWorldDirective directive,
        AgentWorldDirectorMode mode,
        boolean accepted,
        String reason) {

    public AgentWorldDirectivePreview {
        reason = reason == null ? "" : reason.trim();
        if (directive == null || mode == null || (!accepted && reason.isEmpty())) {
            throw new IllegalArgumentException("complete directive preview is required");
        }
    }
}
