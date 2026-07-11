package server.agents.population;

import java.util.Comparator;
import java.util.List;

/** Externally persisted population settings and managed roster. */
public record AgentPopulationSnapshot(boolean enabled,
                                      double multiplier,
                                      List<AgentPopulationRecord> agents) {
    public static final AgentPopulationSnapshot DISABLED = new AgentPopulationSnapshot(false, 1.0, List.of());

    public AgentPopulationSnapshot {
        AgentPopulationPolicy.requireMultiplier(multiplier);
        agents = agents == null ? List.of() : agents.stream()
                .sorted(Comparator.comparing(AgentPopulationRecord::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(AgentPopulationRecord::characterId))
                .toList();
    }
}
