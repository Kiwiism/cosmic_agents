package server.agents.plans;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentPlanStepExecutorRegistry {
    private final Map<String, AgentPlanStepExecutor> executors;

    public AgentPlanStepExecutorRegistry(List<AgentPlanStepExecutor> executors) {
        Map<String, AgentPlanStepExecutor> indexed = new LinkedHashMap<>();
        for (AgentPlanStepExecutor executor : executors) {
            if (executor == null || executor.operation() == null || executor.operation().isBlank()) {
                throw new IllegalArgumentException("A valid plan step executor is required");
            }
            if (indexed.putIfAbsent(executor.operation(), executor) != null) {
                throw new IllegalArgumentException("Duplicate plan operation " + executor.operation());
            }
        }
        this.executors = Map.copyOf(indexed);
    }

    public Optional<AgentPlanStepExecutor> find(String operation) {
        return Optional.ofNullable(executors.get(operation));
    }

    public AgentPlanStepExecutor require(String operation) {
        return find(operation).orElseThrow(() ->
                new AgentPlanValidationException("No executor is registered for plan operation " + operation));
    }
}
