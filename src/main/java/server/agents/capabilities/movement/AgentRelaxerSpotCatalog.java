package server.agents.capabilities.movement;

import java.util.List;

/** @deprecated Maple Island Relaxer locations live with the Amherst plan. */
@Deprecated
public final class AgentRelaxerSpotCatalog {
    public static final int AMHERST_MAP_ID = server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.AMHERST_MAP_ID;
    public static final int SOUTHPERRY_MAP_ID = server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.SOUTHPERRY_MAP_ID;
    public static final int SOUTHPERRY_MIDPOINT_X = server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.SOUTHPERRY_MIDPOINT_X;
    public enum Pool { AMHERST, AMHERST_NEAR_PIO, SOUTHPERRY_ALL, SOUTHPERRY_LEFT, SOUTHPERRY_RIGHT, SOUTHPERRY_FACE_HOLES }
    public record Spot(int mapId, int x, int y) {}
    private AgentRelaxerSpotCatalog() {}
    public static List<Spot> spots(Pool pool) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.spots(mapped(pool)).stream()
                .map(spot -> new Spot(spot.mapId(), spot.x(), spot.y())).toList();
    }
    static server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.Pool mapped(Pool pool) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.Pool.valueOf(pool.name());
    }
}
