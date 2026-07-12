package server.agents.plans.amherst;

public record AmherstPlanValidationIssue(
        AmherstPlanValidationCode code,
        String path,
        String message) {
}
