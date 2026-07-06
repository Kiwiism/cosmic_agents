package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotTickStateRuntime;

import java.util.function.Consumer;

public final class AgentHeartbeatService {
    private AgentHeartbeatService() {
    }

    public static boolean tickHeartbeat(AgentRuntimeEntry entry,
                                        Character agent,
                                        long nowMs,
                                        long intervalMs,
                                        Consumer<Character> lastPacketUpdater,
                                        Consumer<AgentRuntimeEntry> movementBroadcaster) {
        if (!AgentBotTickStateRuntime.heartbeatDue(entry, nowMs, intervalMs)) {
            return false;
        }

        AgentBotTickStateRuntime.markHeartbeat(entry, nowMs);
        lastPacketUpdater.accept(agent);
        movementBroadcaster.accept(entry);
        return true;
    }
}
