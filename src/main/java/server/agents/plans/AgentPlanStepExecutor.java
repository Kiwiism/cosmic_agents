package server.agents.plans;

public interface AgentPlanStepExecutor {
    String operation();

    AgentPlanStepExecution start(AgentPlanExecutionContext context) throws Exception;

    AgentPlanStepExecution tick(AgentPlanExecutionContext context) throws Exception;

    default AgentPlanStepExecution reattach(AgentPlanExecutionContext context) throws Exception {
        return start(context);
    }

    void cancel(AgentPlanExecutionContext context);

    default void validateDefinition(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
    }
}
