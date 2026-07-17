package server.agents.plans.amherst;

import client.Character;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.maps.reservation.CharacterSpaceScope;

import java.util.Optional;

public final class MapleIslandRelaxerSpotReservationRuntime {
    private MapleIslandRelaxerSpotReservationRuntime() {
    }

    public static synchronized Optional<MapleIslandRelaxerSpotCatalog.Spot> reserveRandom(
            int agentCharacterId,
            MapleIslandRelaxerSpotCatalog.Pool pool) {
        return reserveRandom(agentCharacterId, 0, 0, pool);
    }

    public static Optional<MapleIslandRelaxerSpotCatalog.Spot> reserveRandom(
            Character agent,
            MapleIslandRelaxerSpotCatalog.Pool pool) {
        if (agent == null) {
            return Optional.empty();
        }
        if (agent.getClient() == null) {
            return reserveRandom(agent.getId(), pool);
        }
        return reserveRandom(agent.getId(), agent.getWorld(), agent.getClient().getChannel(), pool);
    }

    public static synchronized Optional<MapleIslandRelaxerSpotCatalog.Spot> reserveFromIndex(
            Character agent,
            MapleIslandRelaxerSpotCatalog.Pool pool,
            int startIndex) {
        if (agent == null || pool == null) {
            return Optional.empty();
        }
        int world = agent.getClient() == null ? 0 : agent.getWorld();
        int channel = agent.getClient() == null ? 0 : agent.getClient().getChannel();
        var candidates = MapleIslandRelaxerSpotCatalog.spaces(pool);
        if (agent.getId() <= 0 || candidates.isEmpty()) {
            return Optional.empty();
        }
        CharacterSpaceScope scope = new CharacterSpaceScope(world, channel, pool.mapId());
        CharacterSpaceOwner owner = CharacterSpaceOwner.character(agent.getId());
        int first = Math.floorMod(startIndex, candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            var candidate = candidates.get((first + offset) % candidates.size());
            var reservation = CharacterSpaceReservationRuntime.reserveExact(
                    scope, owner, candidates, candidate, 1);
            if (reservation.isPresent()) {
                return reservation.map(found -> new MapleIslandRelaxerSpotCatalog.Spot(
                        found.scope().mapId(), found.position().x, found.position().y));
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<MapleIslandRelaxerSpotCatalog.Spot> reservedSpot(int agentCharacterId) {
        return CharacterSpaceReservationRuntime.reservation(CharacterSpaceOwner.character(agentCharacterId))
                .map(reservation -> new MapleIslandRelaxerSpotCatalog.Spot(
                        reservation.scope().mapId(),
                        reservation.position().x,
                        reservation.position().y));
    }

    public static synchronized void release(int agentCharacterId) {
        if (agentCharacterId > 0) {
            CharacterSpaceReservationRuntime.release(CharacterSpaceOwner.character(agentCharacterId));
        }
    }

    public static synchronized int occupiedCount() {
        return CharacterSpaceReservationRuntime.occupiedCount();
    }

    public static synchronized void clear() {
        CharacterSpaceReservationRuntime.clear();
    }

    private static Optional<MapleIslandRelaxerSpotCatalog.Spot> reserveRandom(
            int agentCharacterId,
            int worldId,
            int channelId,
            MapleIslandRelaxerSpotCatalog.Pool pool) {
        if (agentCharacterId <= 0 || pool == null) {
            return Optional.empty();
        }
        return CharacterSpaceReservationRuntime.reserveRandom(
                        new CharacterSpaceScope(worldId, channelId, pool.mapId()),
                        CharacterSpaceOwner.character(agentCharacterId),
                        MapleIslandRelaxerSpotCatalog.spaces(pool),
                        1)
                .map(reservation -> new MapleIslandRelaxerSpotCatalog.Spot(
                        reservation.scope().mapId(),
                        reservation.position().x,
                        reservation.position().y));
    }
}
