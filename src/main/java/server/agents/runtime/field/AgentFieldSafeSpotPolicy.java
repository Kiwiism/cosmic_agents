package server.agents.runtime.field;

import client.Character;
import server.agents.field.AgentFarmingCell;
import server.agents.field.AgentNavigationFarmingCellCatalog;
import server.agents.field.AgentFieldPolicyConfig;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Generic reachable low-density rest candidate; authored map overrides may be added later. */
public final class AgentFieldSafeSpotPolicy {
    private AgentFieldSafeSpotPolicy() {
    }

    public static Point select(
            AgentRuntimeEntry entry, Character agent, Set<Integer> relevantMobIds) {
        List<AgentFarmingCell> cells = AgentNavigationFarmingCellCatalog.INSTANCE.cells(entry, agent);
        Point origin = agent == null ? null : agent.getPosition();
        return cells.stream().filter(cell -> !cell.transitOnly())
                .min(Comparator.comparingLong(cell -> score(cell, relevantMobIds, origin)))
                .map(cell -> cell.anchors().getFirst().position()).orElse(null);
    }

    private static long score(AgentFarmingCell cell, Set<Integer> relevantMobIds, Point origin) {
        int population = cell.relevantPopulation(relevantMobIds);
        Point anchor = cell.anchors().getFirst().position();
        long distance = origin == null ? 0L : Math.round(Math.sqrt(origin.distanceSq(anchor)));
        return population * AgentFieldPolicyConfig.safeSpotPopulationWeight()
                + (cell.deadEnd() ? AgentFieldPolicyConfig.safeSpotDeadEndPenalty() : 0L)
                + distance;
    }
}
