package server.agents.economy.scenario;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** External-state gate run after evidence ingestion and before a logical day may close. */
@FunctionalInterface
public interface EconomyDayCloseReconciler {
    Result reconcile(UUID runId, Map<String, EconomyRunCoordinator.AgentView> agents, Instant logicalAt);

    static EconomyDayCloseReconciler ledgerOnly() {
        return (runId, agents, at) -> new Result(true, List.of());
    }

    record Result(boolean clean, List<String> violations) {
        public Result { violations = List.copyOf(violations); }
    }
}
