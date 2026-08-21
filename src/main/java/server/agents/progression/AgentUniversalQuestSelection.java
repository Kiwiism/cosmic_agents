package server.agents.progression;

import server.agents.progression.questcatalog.AgentQuestDefinition;

import java.util.List;

/** Explainable ranking result; accepting the quest remains an external action. */
public record AgentUniversalQuestSelection(
        AgentQuestDefinition quest,
        long score,
        int routeHops,
        List<String> evidence) {

    public AgentUniversalQuestSelection {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        if (quest == null || routeHops < 0 || evidence.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("complete universal quest selection evidence is required");
        }
    }
}
