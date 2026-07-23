package server.agents.capabilities.navigation;

import server.life.Monster;
import server.maps.MapleMap;

import java.util.HashMap;
import java.util.Map;

/** Adds a soft route cost for intermediate ground regions occupied by hostile monsters. */
final class AgentNavigationDangerCostService {
    private static final int COST_PER_MONSTER_MS = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentNavigationDangerCostService.COST_PER_MONSTER_MS");
    private static final int MAX_REGION_COST_MS = config.AgentTuning.intValue("server.agents.capabilities.navigation.AgentNavigationDangerCostService.MAX_REGION_COST_MS");

    private AgentNavigationDangerCostService() {
    }

    static Map<Integer, Integer> intermediateRegionCosts(AgentNavigationGraph graph,
                                                         MapleMap map,
                                                         int startRegionId,
                                                         int targetRegionId) {
        if (graph == null || map == null || map.getFootholds() == null) {
            return Map.of();
        }

        Map<Integer, Integer> costs = new HashMap<>();
        for (Monster monster : map.getAllMonsters()) {
            if (monster == null || !monster.isAlive()
                    || monster.getPosition() == null
                    || monster.getStats() != null && monster.getStats().isFriendly()) {
                continue;
            }
            int regionId = graph.findRegionId(map, monster.getPosition());
            AgentNavigationGraph.Region region = graph.getRegion(regionId);
            if (regionId < 0 || regionId == startRegionId || regionId == targetRegionId
                    || region == null || region.isRopeRegion) {
                continue;
            }
            costs.compute(regionId, (ignored, current) -> Math.min(
                    MAX_REGION_COST_MS,
                    (current == null ? 0 : current) + COST_PER_MONSTER_MS));
        }
        return costs.isEmpty() ? Map.of() : Map.copyOf(costs);
    }
}
