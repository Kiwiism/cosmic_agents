package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.runtime.AgentCapabilityActionSubmission;
import server.agents.capabilities.runtime.AgentCapabilityView;
import server.agents.model.AgentPosition;
import server.agents.model.AgentSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.awt.Point;
import client.Job;

/** Converts mutable Cosmic state into the narrow capability-facing view. */
public final class CosmicAgentCapabilityViewFactory {
    private CosmicAgentCapabilityViewFactory() {
    }

    public static AgentCapabilityView create(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return AgentCapabilityView.unavailable();
        }
        Point position = agent.getPosition();
        if (position == null) {
            position = new Point();
        }
        Job job = agent.getJob();
        String name = agent.getName();
        AgentSnapshot snapshot = new AgentSnapshot(
                agent.getId(),
                name == null ? "" : name,
                Math.max(0, agent.getMapId()),
                agent.getLevel(),
                job == null ? 0 : job.getId(),
                new AgentPosition(position.x, position.y),
                agent.isAlive());
        return new AgentCapabilityView(
                snapshot,
                CosmicAgentPerceptionSnapshotFactory.capture(agent, nowMs),
                entry.capabilityStates(),
                AgentSessionEventRuntime.bus(entry),
                ignored -> AgentCapabilityActionSubmission.UNSUPPORTED);
    }
}
