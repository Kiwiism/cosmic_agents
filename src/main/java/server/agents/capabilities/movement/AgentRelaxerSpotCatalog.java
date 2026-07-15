package server.agents.capabilities.movement;

import server.maps.reservation.CharacterSpace;
import server.maps.reservation.MapleIslandCharacterSpaceCatalog;

import java.util.List;

/**
 * Immutable Relaxer destinations sampled once from the v83 Maple Island footholds.
 */
public final class AgentRelaxerSpotCatalog {
    public static final int AMHERST_MAP_ID = MapleIslandCharacterSpaceCatalog.AMHERST_MAP_ID;
    public static final int SOUTHPERRY_MAP_ID = MapleIslandCharacterSpaceCatalog.SOUTHPERRY_MAP_ID;
    public static final int SOUTHPERRY_MIDPOINT_X = MapleIslandCharacterSpaceCatalog.SOUTHPERRY_MIDPOINT_X;

    public enum Pool {
        AMHERST(AMHERST_MAP_ID),
        SOUTHPERRY_ALL(SOUTHPERRY_MAP_ID),
        SOUTHPERRY_LEFT(SOUTHPERRY_MAP_ID),
        SOUTHPERRY_RIGHT(SOUTHPERRY_MAP_ID);

        private final int mapId;

        Pool(int mapId) {
            this.mapId = mapId;
        }

        public int mapId() {
            return mapId;
        }
    }

    public record Spot(int mapId, int x, int y) {
    }

    private AgentRelaxerSpotCatalog() {
    }

    public static List<Spot> spots(Pool pool) {
        return spaces(pool).stream()
                .map(space -> new Spot(space.mapId(), space.x(), space.y()))
                .toList();
    }

    static List<CharacterSpace> spaces(Pool pool) {
        return switch (pool) {
            case AMHERST -> MapleIslandCharacterSpaceCatalog.amherst();
            case SOUTHPERRY_ALL -> MapleIslandCharacterSpaceCatalog.southperry();
            case SOUTHPERRY_LEFT -> MapleIslandCharacterSpaceCatalog.southperryLeft();
            case SOUTHPERRY_RIGHT -> MapleIslandCharacterSpaceCatalog.southperryRight();
        };
    }
}
