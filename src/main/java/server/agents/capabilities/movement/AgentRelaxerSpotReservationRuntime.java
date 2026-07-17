package server.agents.capabilities.movement;

import client.Character;
import java.util.Optional;

/** @deprecated Maple Island Relaxer reservation lives with the Amherst plan. */
@Deprecated
public final class AgentRelaxerSpotReservationRuntime {
    private AgentRelaxerSpotReservationRuntime() {}
    public static Optional<AgentRelaxerSpotCatalog.Spot> reserveRandom(int agentId, AgentRelaxerSpotCatalog.Pool pool) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime
                .reserveRandom(agentId, AgentRelaxerSpotCatalog.mapped(pool)).map(AgentRelaxerSpotReservationRuntime::mapped);
    }
    public static Optional<AgentRelaxerSpotCatalog.Spot> reserveRandom(Character agent, AgentRelaxerSpotCatalog.Pool pool) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime
                .reserveRandom(agent, AgentRelaxerSpotCatalog.mapped(pool)).map(AgentRelaxerSpotReservationRuntime::mapped);
    }
    public static Optional<AgentRelaxerSpotCatalog.Spot> reserveFromIndex(Character agent, AgentRelaxerSpotCatalog.Pool pool, int index) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime
                .reserveFromIndex(agent, AgentRelaxerSpotCatalog.mapped(pool), index).map(AgentRelaxerSpotReservationRuntime::mapped);
    }
    public static Optional<AgentRelaxerSpotCatalog.Spot> reservedSpot(int id) {
        return server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime.reservedSpot(id)
                .map(AgentRelaxerSpotReservationRuntime::mapped);
    }
    public static void release(int id) { server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime.release(id); }
    static int occupiedCount() { return server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime.occupiedCount(); }
    static void clear() { server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime.clear(); }
    private static AgentRelaxerSpotCatalog.Spot mapped(server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog.Spot spot) {
        return new AgentRelaxerSpotCatalog.Spot(spot.mapId(), spot.x(), spot.y());
    }
}
