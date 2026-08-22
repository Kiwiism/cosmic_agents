package server.agents.runtime.activity.control.binding;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityTransferPort;

/** Normal-world travel boundary shared by every Director activity adapter. */
@FunctionalInterface
public interface AgentWorldMapTransfer {
    AgentActivityTransferPort.Result travel(
            AgentRuntimeEntry entry, Character agent, int destinationMapId, long nowMs);
}
