package server.agents.runtime.simulation;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

public final class AgentDefaultSimulationPolicy implements AgentSimulationPolicy {
    private final boolean enabled;
    private final boolean backgroundAbstractEnabled;
    private final MapGateway maps;
    private final AgentBackgroundExecutionPolicy backgroundExecutionPolicy;

    public AgentDefaultSimulationPolicy(boolean enabled,
                                        boolean backgroundAbstractEnabled,
                                        MapGateway maps,
                                        AgentBackgroundExecutionPolicy backgroundExecutionPolicy) {
        if (maps == null || backgroundExecutionPolicy == null) {
            throw new IllegalArgumentException("Agent simulation policy dependencies are required");
        }
        this.enabled = enabled;
        this.backgroundAbstractEnabled = backgroundAbstractEnabled;
        this.maps = maps;
        this.backgroundExecutionPolicy = backgroundExecutionPolicy;
    }

    @Override
    public AgentSimulationMode selectMode(AgentRuntimeEntry entry) {
        if (!enabled) {
            return AgentSimulationMode.PRESENTATION;
        }
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        if (map == null || maps.isObservedByPlayer(map)) {
            return AgentSimulationMode.PRESENTATION;
        }
        if (backgroundAbstractEnabled && backgroundExecutionPolicy.permitsAbstractExecution(entry, map)) {
            return AgentSimulationMode.BACKGROUND_ABSTRACT;
        }
        return AgentSimulationMode.BACKGROUND_ACTIVE;
    }
}
