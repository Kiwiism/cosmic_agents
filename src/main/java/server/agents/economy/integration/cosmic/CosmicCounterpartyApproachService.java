package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.capabilities.primitive.AgentNavigationCapability;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;

/** Physical approach used before an autonomous agent-to-agent Trade invitation. */
final class CosmicCounterpartyApproachService {
    private final int rangePixels;
    private final long timeoutMs;

    CosmicCounterpartyApproachService(int rangePixels, long timeoutMs) {
        if (rangePixels <= 0 || timeoutMs <= 0) throw new IllegalArgumentException();
        this.rangePixels = rangePixels;
        this.timeoutMs = timeoutMs;
    }

    Status request(Character mover, Character target) {
        if (mover == null || target == null || mover == target || mover.getMap() == null
                || mover.getMap() != target.getMap() || mover.getMapId() < 910000000
                || mover.getMapId() > 910000022) return Status.UNAVAILABLE;
        if (mover.getPosition().distanceSq(target.getPosition())
                <= (long) rangePixels * rangePixels) return Status.ARRIVED;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(mover);
        if (entry == null) return Status.UNAVAILABLE;
        if (entry.capabilityRuntimeState().hasActiveCapability()) return Status.IN_PROGRESS;
        Point destination = new Point(target.getPosition());
        boolean assigned = AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                new AgentNavigationCapability(), new AgentNavigationCapability.Command(
                mover.getMapId(), destination, rangePixels, true), timeoutMs, 2));
        return assigned ? Status.ASSIGNED : Status.IN_PROGRESS;
    }

    enum Status { ARRIVED, ASSIGNED, IN_PROGRESS, UNAVAILABLE }
}
