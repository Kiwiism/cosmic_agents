package server.agents.capabilities.townlife;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.reservation.CharacterSpace;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts navigation regions into categorized, capacity-bearing TownLife reservation slots. */
public final class AgentTownLifePlatformCatalog {
    public record PlatformSpot(CharacterSpace space,
                               AgentTownLifeState.District district,
                               AgentTownLifeState.PlatformKind platformKind) {
    }

    private AgentTownLifePlatformCatalog() {
    }

    public static List<PlatformSpot> reachable(AgentNavigationGraph graph,
                                               AgentTownLifeProfile profile,
                                               int originComponent) {
        if (graph == null || profile == null) {
            return List.of();
        }
        AgentTownLifeProfile.Geometry geometry = profile.geometry();
        AgentTownLifeMapExtension extension =
                AgentTownLifeMapExtensionRepository.forMap(profile.mapId());
        List<PlatformSpot> result = new ArrayList<>();
        Map<String, Integer> nextSpotNumber = new HashMap<>();
        for (AgentNavigationGraph.Region region : graph.regions.stream()
                .sorted(Comparator.comparingInt(candidate -> candidate.id)).toList()) {
            if (region.isRopeRegion || region.width() < geometry.minimumPlatformWidth()
                    || (originComponent >= 0
                    && graph.connectedComponentId(region.id) != originComponent)) {
                continue;
            }
            int count = Math.min(geometry.maxSlotsPerPlatform(),
                    Math.max(1, region.width() / geometry.slotSpacingPx()));
            int inset = Math.min(geometry.edgeInsetPx(), Math.max(8, region.width() / 8));
            for (int slot = 0; slot < count; slot++) {
                int usableWidth = Math.max(0, region.width() - inset * 2);
                int x = region.minX + inset + (slot + 1) * usableWidth / (count + 1);
                Point point = region.pointAt(x);
                AgentTownLifeState.District district = extension.classify(point);
                AgentTownLifeState.PlatformKind platformKind =
                        extension.classifyPlatform(region.width());
                String catalogId = "town-nav-" + profile.mapId() + '-'
                        + district.name().toLowerCase() + '-'
                        + platformKind.name().toLowerCase();
                int spotNumber = nextSpotNumber.merge(catalogId, 1, Integer::sum);
                CharacterSpace space = new CharacterSpace(
                        catalogId, spotNumber, profile.mapId(),
                        Math.max(0, region.id), slot, point.x, point.y);
                result.add(new PlatformSpot(space, district, platformKind));
            }
        }
        return List.copyOf(result);
    }
}
