package server.agents.runtime;

import server.agents.events.AgentDomainEvent;
import server.agents.events.AgentEventPriority;

import java.util.Map;

/** Single lifecycle transition and event-emission boundary. */
public final class AgentLifecycleTransitionService {
    private AgentLifecycleTransitionService() {
    }

    public static boolean transition(AgentRuntimeEntry entry, AgentLifecyclePhase next, String reason) {
        if (entry == null || next == null) {
            return false;
        }
        AgentLifecyclePhase previous = entry.lifecycleState().phase();
        if (previous == next) {
            return true;
        }
        entry.lifecycleState().transition(next, reason);
        int agentId = entry.bot() == null ? 0 : entry.bot().getId();
        if (agentId > 0) {
            long nowMs = System.currentTimeMillis();
            AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(agentId, nowMs,
                            "lifecycle.transition", previous + ":" + next + ':'
                                    + entry.lifecycleState().sequence(),
                            Map.of("from", previous.name(), "to", next.name(),
                                    "reason", reason == null ? "" : reason)),
                    next == AgentLifecyclePhase.FAILED || next == AgentLifecyclePhase.QUARANTINED
                            ? AgentEventPriority.CRITICAL : AgentEventPriority.IMPORTANT);
        }
        return true;
    }
}
