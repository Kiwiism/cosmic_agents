package server.agents.capabilities.supplies;

import client.Character;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;
import java.util.function.ToIntFunction;

public final class AgentGroupSupplyResponderSelector {
    private AgentGroupSupplyResponderSelector() {
    }

    public static <E extends AgentRuntimeHandle> E select(
            Character leader,
            List<E> entries,
            ToIntFunction<E> agentMapId) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int leaderMapId = leader != null ? leader.getMapId() : -1;
        for (E entry : entries) {
            if (agentMapId.applyAsInt(entry) == leaderMapId) {
                return entry;
            }
        }
        return entries.get(0);
    }
}
