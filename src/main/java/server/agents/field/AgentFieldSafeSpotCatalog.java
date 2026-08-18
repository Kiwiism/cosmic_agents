package server.agents.field;

import java.util.List;

/** Authored mob-free resting and inactive-observer positions for field maps. */
public record AgentFieldSafeSpotCatalog(int schemaVersion, List<MapSpots> maps) {
    public AgentFieldSafeSpotCatalog {
        if (schemaVersion != 1 || maps == null) {
            throw new IllegalArgumentException("valid field safe-spot catalog is required");
        }
        maps = List.copyOf(maps);
    }

    public record MapSpots(int mapId, String mapName, List<Spot> spots) {
        public MapSpots {
            if (mapId <= 0 || mapName == null || mapName.isBlank()
                    || spots == null || spots.isEmpty()) {
                throw new IllegalArgumentException("valid field safe-spot map is required");
            }
            spots = List.copyOf(spots);
        }
    }

    public record Spot(int x, int y, String label) {
        public Spot {
            label = label == null ? "" : label.trim();
        }
    }
}
