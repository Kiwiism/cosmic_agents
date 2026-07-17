package server.agents.capabilities.objective;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

@FunctionalInterface
public interface AgentNpcInteractionPlacementPolicy {
    AgentNpcInteractionPlacementPolicy DIRECT = (entry, agent, mapId, npcId,
                                                   currentPosition, npcPosition, defaultRangePx) ->
            new Placement(defaultRangePx, null, false);

    Placement select(AgentRuntimeEntry entry,
                     Character agent,
                     int mapId,
                     int npcId,
                     Point currentPosition,
                     Point npcPosition,
                     int defaultRangePx);

    default boolean distinguishesInteractionStages(AgentRuntimeEntry entry) {
        return false;
    }

    record Placement(int interactionRangePx, Point anchor, boolean climbable) {
        public Placement {
            if (interactionRangePx <= 0) {
                throw new IllegalArgumentException("interaction range must be positive");
            }
            anchor = anchor == null ? null : new Point(anchor);
        }
    }
}
