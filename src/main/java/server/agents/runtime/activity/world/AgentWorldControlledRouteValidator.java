package server.agents.runtime.activity.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structural validation only; it does not resolve or start child requests. */
public final class AgentWorldControlledRouteValidator {
    private AgentWorldControlledRouteValidator() {
    }

    public static Result validate(
            AgentWorldControlledRoute route,
            AgentWorldActivityAdapterCatalog adapters,
            Set<String> availableAuthoredPlanIds) {
        if (route == null || adapters == null || availableAuthoredPlanIds == null) {
            throw new IllegalArgumentException(
                    "route, adapter catalog, and authored plan ids are required");
        }
        List<String> issues = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        AgentWorldMilestone previousTerminal = null;
        for (AgentWorldControlledRoute.Stage stage : route.stages()) {
            if (!ids.add(stage.stageId())) {
                issues.add("duplicate stage id " + stage.stageId());
            }
            AgentWorldActivityAdapterCatalog.Coverage coverage = adapters.coverage(stage.kind());
            if (coverage == null || !coverage.complete()) {
                issues.add(stage.stageId() + " has no complete " + stage.kind()
                        + " activity adapter");
            }
            if (stage.requestType() == AgentWorldActivityRequestType.AUTHORED_PLAN
                    && !availableAuthoredPlanIds.contains(stage.requestId())) {
                issues.add(stage.stageId() + " references missing authored plan "
                        + stage.requestId());
            }
            if (stage.requiredMilestone() != null && previousTerminal != null
                    && stage.requiredMilestone() != previousTerminal) {
                issues.add(stage.stageId() + " requires " + stage.requiredMilestone()
                        + " after " + previousTerminal);
            }
            previousTerminal = stage.terminalMilestone();
        }
        return new Result(issues.isEmpty(), issues);
    }

    public record Result(boolean valid, List<String> issues) {
        public Result {
            issues = List.copyOf(issues == null ? List.of() : issues);
            if (valid && !issues.isEmpty() || !valid && issues.isEmpty()) {
                throw new IllegalArgumentException("route validity must match its issues");
            }
        }
    }
}
