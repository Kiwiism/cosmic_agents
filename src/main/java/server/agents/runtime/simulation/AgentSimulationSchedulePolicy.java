package server.agents.runtime.simulation;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentWorkClass;

public final class AgentSimulationSchedulePolicy {
    private final AgentSchedulerConfig config;
    private final AgentSimulationPolicy simulationPolicy;
    private final AgentSimulationTransitionService transitionService;

    public AgentSimulationSchedulePolicy(AgentSchedulerConfig config,
                                         AgentSimulationPolicy simulationPolicy,
                                         AgentSimulationTransitionService transitionService) {
        if (config == null || simulationPolicy == null || transitionService == null) {
            throw new IllegalArgumentException("Agent simulation schedule dependencies are required");
        }
        this.config = config;
        this.simulationPolicy = simulationPolicy;
        this.transitionService = transitionService;
    }

    public AgentSimulationScheduleDecision decide(AgentRuntimeEntry entry,
                                                  long basePeriodMs,
                                                  AgentWorkClass baseWorkClass,
                                                  AgentPriorityClass basePriority,
                                                  long nowMs) {
        AgentSimulationMode mode = transitionService.transition(
                entry,
                simulationPolicy.selectMode(entry),
                nowMs);
        return switch (mode) {
            case PRESENTATION -> new AgentSimulationScheduleDecision(
                    mode, basePeriodMs, baseWorkClass, basePriority);
            case BACKGROUND_ACTIVE -> new AgentSimulationScheduleDecision(
                    mode,
                    Math.max(basePeriodMs, config.backgroundActiveTickMs()),
                    AgentWorkClass.BACKGROUND_GAMEPLAY,
                    AgentPriorityClass.BACKGROUND_ACTIVE);
            case BACKGROUND_ABSTRACT -> new AgentSimulationScheduleDecision(
                    mode,
                    Math.max(basePeriodMs, config.backgroundAbstractHeartbeatMs()),
                    AgentWorkClass.BACKGROUND_GAMEPLAY,
                    AgentPriorityClass.BACKGROUND_ABSTRACT);
        };
    }
}
