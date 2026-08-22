package server.agents.integration;

import java.util.Objects;

public record AgentIdentityRecord(int characterId,
                                  AgentIdentityStatus status,
                                  AgentIdentityOrigin origin,
                                  boolean interactiveAllowed) {
    public AgentIdentityRecord {
        if (characterId <= 0) {
            throw new IllegalArgumentException("Agent character id must be positive");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(origin, "origin");
    }

    public boolean isActive() {
        return status == AgentIdentityStatus.ACTIVE;
    }
}
