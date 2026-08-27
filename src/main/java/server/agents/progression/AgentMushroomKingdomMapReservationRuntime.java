package server.agents.progression;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Timed, process-local reservations for Mushroom Kingdom hunt-map travel. */
final class AgentMushroomKingdomMapReservationRuntime {
    enum LeaseState { MISSING, TRAVELING, OCCUPYING, LEFT }

    private record Reservation(int agentId, int world, int channel, int mapId,
                               long expiresAtMs, boolean arrived) {
    }

    private static final Map<Integer, Reservation> BY_AGENT_ID = new HashMap<>();

    private AgentMushroomKingdomMapReservationRuntime() {
    }

    static synchronized Optional<AgentMushroomKingdomHuntMapSelector.Selection> selectAndReserve(
            int agentId,
            int world,
            int channel,
            List<AgentMushroomKingdomCatalog.HuntMap> rankedMaps,
            Map<Integer, Integer> liveOccupancy,
            long nowMs,
            long leaseMs) {
        if (agentId <= 0 || world < 0 || channel < 0 || leaseMs <= 0) {
            throw new IllegalArgumentException("valid Mushroom Kingdom map reservation scope is required");
        }
        purgeExpired(nowMs);
        BY_AGENT_ID.remove(agentId);
        Map<Integer, Integer> effectiveOccupancy = new LinkedHashMap<>();
        for (AgentMushroomKingdomCatalog.HuntMap map : rankedMaps) {
            int live = liveOccupancy == null ? 0
                    : Math.max(0, liveOccupancy.getOrDefault(map.mapId(), 0));
            long pending = BY_AGENT_ID.values().stream()
                    .filter(reservation -> !reservation.arrived()
                            && reservation.world() == world
                            && reservation.channel() == channel
                            && reservation.mapId() == map.mapId())
                    .count();
            long arrived = BY_AGENT_ID.values().stream()
                    .filter(reservation -> reservation.arrived()
                            && reservation.world() == world
                            && reservation.channel() == channel
                            && reservation.mapId() == map.mapId())
                    .count();
            int present = Math.max(live, Math.toIntExact(arrived));
            effectiveOccupancy.put(map.mapId(), Math.addExact(present, Math.toIntExact(pending)));
        }
        Optional<AgentMushroomKingdomHuntMapSelector.Selection> selected =
                AgentMushroomKingdomHuntMapSelector.select(rankedMaps, effectiveOccupancy);
        selected.ifPresent(decision -> BY_AGENT_ID.put(agentId, new Reservation(
                agentId, world, channel, decision.map().mapId(),
                Math.addExact(nowMs, leaseMs), false)));
        return selected;
    }

    static synchronized LeaseState maintain(int agentId, int selectedMapId,
                                            int currentMapId, long nowMs, long leaseMs) {
        purgeExpired(nowMs);
        Reservation reservation = BY_AGENT_ID.get(agentId);
        if (reservation == null || reservation.mapId() != selectedMapId) {
            return LeaseState.MISSING;
        }
        if (currentMapId == selectedMapId) {
            BY_AGENT_ID.put(agentId, new Reservation(
                    reservation.agentId(), reservation.world(), reservation.channel(),
                    reservation.mapId(), Math.addExact(nowMs, leaseMs), true));
            return LeaseState.OCCUPYING;
        }
        if (reservation.arrived()) {
            BY_AGENT_ID.remove(agentId);
            return LeaseState.LEFT;
        }
        return LeaseState.TRAVELING;
    }

    static synchronized void release(int agentId) {
        if (agentId > 0) BY_AGENT_ID.remove(agentId);
    }

    static synchronized int reservationCount() {
        return BY_AGENT_ID.size();
    }

    static synchronized void clear() {
        BY_AGENT_ID.clear();
    }

    private static void purgeExpired(long nowMs) {
        BY_AGENT_ID.values().removeIf(reservation -> nowMs >= reservation.expiresAtMs());
    }
}
