package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.plans.AgentPlanDefinition;
import server.agents.plans.AgentPlanRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldControlledRouteValidatorTest {
    @Test
    void level15RouteIsStructurallyReadyButLevel30ReportsKpqAdapterGap() {
        AgentWorldActivityAdapterCatalog adapters = AgentWorldActivityAdapterCatalog.current();
        java.util.Set<String> planIds = AgentPlanRepository.defaultRepository().all().stream()
                .map(AgentPlanDefinition::planId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        adapters.all().stream().filter(
                AgentWorldActivityAdapterCatalog.Coverage::complete).forEach(coverage -> {
            try {
                Class.forName(coverage.adapterClassName());
            } catch (ClassNotFoundException missing) {
                throw new AssertionError("Missing activity adapter "
                        + coverage.adapterClassName(), missing);
            }
        });

        assertTrue(AgentWorldControlledRouteValidator.validate(
                AgentWorldControlledRoute.level15(), adapters, planIds).valid());
        AgentWorldControlledRouteValidator.Result level30 =
                AgentWorldControlledRouteValidator.validate(
                        AgentWorldControlledRoute.level30(), adapters, planIds);

        assertFalse(level30.valid());
        assertTrue(level30.issues().stream().anyMatch(issue -> issue.contains("PARTY_QUEST")));
        assertTrue(level30.issues().stream().anyMatch(
                issue -> issue.contains("victoria-second-job")));
    }
}
