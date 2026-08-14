package server.agents.economy.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionEvidence(UUID decisionId, UUID runId, String agentId, Instant logicalTime,
                               String decisionKind, Map<String, Object> chosenAction,
                               List<Map<String, Object>> alternatives,
                               Map<String, Object> beliefsUsed, Map<String, Object> needsUsed,
                               Map<String, Object> utilityBreakdown, String randomStream,
                               Double randomDraw, String configHash, String catalogVersion) {
    public DecisionEvidence {
        if (decisionId == null || runId == null || agentId == null || agentId.isBlank()
                || logicalTime == null || decisionKind == null || decisionKind.isBlank()
                || configHash == null || catalogVersion == null) throw new IllegalArgumentException();
        chosenAction = Map.copyOf(chosenAction);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        beliefsUsed = beliefsUsed == null ? Map.of() : Map.copyOf(beliefsUsed);
        needsUsed = needsUsed == null ? Map.of() : Map.copyOf(needsUsed);
        utilityBreakdown = utilityBreakdown == null ? Map.of() : Map.copyOf(utilityBreakdown);
    }
}
