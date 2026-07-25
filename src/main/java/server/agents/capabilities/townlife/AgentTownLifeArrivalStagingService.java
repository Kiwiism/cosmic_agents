package server.agents.capabilities.townlife;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentArrivalStagingService;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves authored arrival markers onto real graph surfaces before an Agent is released. */
public final class AgentTownLifeArrivalStagingService {
    private AgentTownLifeArrivalStagingService() {
    }

    public static Point select(AgentTownLifeProfile profile,
                               MapleMap map,
                               AgentNavigationGraph graph,
                               int identitySeed) {
        if (profile == null || map == null || graph == null) {
            throw new IllegalArgumentException("town profile, map and navigation graph are required");
        }
        String preferred = profile.arrivalPortal(identitySeed);
        List<AgentTownLifeProfile.ArrivalPortal> candidates = new ArrayList<>(profile.arrivalPortals());
        candidates.sort(Comparator.comparing((AgentTownLifeProfile.ArrivalPortal portal) ->
                !portal.name().equals(preferred)));
        return AgentArrivalStagingService.select(
                map, graph, candidates.stream()
                        .map(AgentTownLifeProfile.ArrivalPortal::name)
                        .toList());
    }
}
