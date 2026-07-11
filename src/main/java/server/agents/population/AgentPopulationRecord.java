package server.agents.population;

import java.util.Objects;

/** An explicitly managed Agent backing character. */
public record AgentPopulationRecord(int characterId, String name, Integer crewId) {
    public AgentPopulationRecord {
        if (characterId <= 0) {
            throw new IllegalArgumentException("characterId must be positive");
        }
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (crewId != null && crewId < 0) {
            throw new IllegalArgumentException("crewId must be nonnegative");
        }
    }

    public AgentPopulationRecord withCrew(Integer newCrewId) {
        return new AgentPopulationRecord(characterId, name, newCrewId);
    }
}
