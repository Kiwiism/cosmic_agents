package server.agents.runtime.scheduler;

@FunctionalInterface
public interface AgentLoadSheddingPolicy {
    AgentLoadSheddingRecommendation recommend(
            AgentSchedulerPressureSample sample,
            AgentLoadSheddingConfig config);
}
