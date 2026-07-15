package server.agents.capabilities.movement;

import client.Character;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.maps.reservation.CharacterSpaceScope;

import java.util.Optional;

public final class AgentRelaxerSpotReservationRuntime {
    private AgentRelaxerSpotReservationRuntime() {
    }

    public static synchronized Optional<AgentRelaxerSpotCatalog.Spot> reserveRandom(
            int agentCharacterId,
            AgentRelaxerSpotCatalog.Pool pool) {
        return reserveRandom(agentCharacterId, 0, 0, pool);
    }

    public static Optional<AgentRelaxerSpotCatalog.Spot> reserveRandom(
            Character agent,
            AgentRelaxerSpotCatalog.Pool pool) {
        if (agent == null) {
            return Optional.empty();
        }
        if (agent.getClient() == null) {
            return reserveRandom(agent.getId(), pool);
        }
        return reserveRandom(agent.getId(), agent.getWorld(), agent.getClient().getChannel(), pool);
    }

    public static synchronized Optional<AgentRelaxerSpotCatalog.Spot> reservedSpot(int agentCharacterId) {
        return CharacterSpaceReservationRuntime.reservation(CharacterSpaceOwner.character(agentCharacterId))
                .map(reservation -> new AgentRelaxerSpotCatalog.Spot(
                        reservation.scope().mapId(),
                        reservation.position().x,
                        reservation.position().y));
    }

    public static synchronized void release(int agentCharacterId) {
        if (agentCharacterId > 0) {
            CharacterSpaceReservationRuntime.release(CharacterSpaceOwner.character(agentCharacterId));
        }
    }

    static synchronized int occupiedCount() {
        return CharacterSpaceReservationRuntime.occupiedCount();
    }

    static synchronized void clear() {
        CharacterSpaceReservationRuntime.clear();
    }

    private static Optional<AgentRelaxerSpotCatalog.Spot> reserveRandom(
            int agentCharacterId,
            int worldId,
            int channelId,
            AgentRelaxerSpotCatalog.Pool pool) {
        if (agentCharacterId <= 0 || pool == null) {
            return Optional.empty();
        }
        return CharacterSpaceReservationRuntime.reserveRandom(
                        new CharacterSpaceScope(worldId, channelId, pool.mapId()),
                        CharacterSpaceOwner.character(agentCharacterId),
                        AgentRelaxerSpotCatalog.spaces(pool),
                        1)
                .map(reservation -> new AgentRelaxerSpotCatalog.Spot(
                        reservation.scope().mapId(),
                        reservation.position().x,
                        reservation.position().y));
    }
}
