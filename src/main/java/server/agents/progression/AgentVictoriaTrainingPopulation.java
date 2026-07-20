package server.agents.progression;

import client.Character;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.MapGateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Read-only population view that never loads a map merely to count it. */
final class AgentVictoriaTrainingPopulation {
    private AgentVictoriaTrainingPopulation() {
    }

    static Map<Integer, Integer> snapshot(Character agent, Set<Integer> mapIds) {
        return snapshot(agent, mapIds, AgentMapGatewayRuntime.map());
    }

    static Map<Integer, Integer> snapshot(Character agent, Set<Integer> mapIds, MapGateway maps) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (agent == null || agent.getClient() == null || mapIds == null) {
            return Map.of();
        }
        int world = agent.getWorld();
        int channel = agent.getClient().getChannel();
        for (int mapId : mapIds) {
            result.put(mapId, Math.max(0,
                    maps.activeCharacterCountIfLoaded(world, channel, mapId)));
        }
        return Map.copyOf(result);
    }
}
