package server.agents.journey;

import java.util.Map;

/** A reconciliation fact derived from two snapshots, not a second gameplay event. */
record AgentJourneyDerivedEvidence(
        String category,
        String eventType,
        boolean critical,
        Map<String, Object> attributes) {

    AgentJourneyDerivedEvidence {
        category = category == null || category.isBlank() ? "reconciliation" : category;
        eventType = eventType == null ? "" : eventType;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
