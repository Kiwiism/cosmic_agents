package server.agents.plans.amherst;

import java.util.List;

public final class AmherstPlanValidationException extends Exception {
    private final List<AmherstPlanValidationIssue> issues;

    public AmherstPlanValidationException(List<AmherstPlanValidationIssue> issues) {
        super(issues.isEmpty() ? "Amherst plan validation failed" : issues.get(0).message());
        this.issues = List.copyOf(issues);
    }

    public List<AmherstPlanValidationIssue> issues() {
        return issues;
    }
}
