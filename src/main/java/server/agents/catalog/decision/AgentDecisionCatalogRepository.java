package server.agents.catalog.decision;

/** Read-only repository boundary for a single atomically loaded decision-catalog snapshot. */
public interface AgentDecisionCatalogRepository {
    AgentDecisionCatalogSnapshot snapshot();
}
