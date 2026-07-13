package server.agents.runtime.simulation;

import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

@FunctionalInterface
public interface AgentBackgroundExecutionPolicy {
    boolean permitsAbstractExecution(AgentRuntimeEntry entry, MapleMap map);

    static AgentBackgroundExecutionPolicy denyAll() {
        return (entry, map) -> false;
    }
}
