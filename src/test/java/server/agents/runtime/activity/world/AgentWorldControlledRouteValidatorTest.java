package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.plans.AgentPlanDefinition;
import server.agents.plans.AgentPlanRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldControlledRouteValidatorTest {
    @Test
    void level15AndLevel30RoutesAreReady() {
        AgentWorldActivityAdapterCatalog adapters = AgentWorldActivityAdapterCatalog.current();
        java.util.Set<String> planIds = AgentPlanRepository.defaultRepository().all().stream()
                .map(AgentPlanDefinition::planId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertTrue(adapters.all().stream().allMatch(
                AgentWorldActivityAdapterCatalog.Coverage::complete));

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

        assertTrue(level30.valid(), () -> String.join("; ", level30.issues()));
    }
}
