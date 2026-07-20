package server.agents.capabilities.inventory;

import server.agents.capabilities.contracts.AgentInventoryReservation;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One authoritative reservation ledger shared by loot, equipment, quest, shop, and trade policy. */
public final class AgentInventoryReservationLedger {
    public static final AgentCapabilityStateKey<AgentInventoryReservationLedger> STATE_KEY =
            new AgentCapabilityStateKey<>("inventory.reservations", AgentInventoryReservationLedger.class,
                    AgentInventoryReservationLedger::new);

    private final Map<String, AgentInventoryReservation> reservations = new LinkedHashMap<>();

    public synchronized void reserve(AgentInventoryReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("Inventory reservation is required");
        }
        reservations.put(reservation.reservationId(), reservation);
    }

    public synchronized boolean release(String reservationId) {
        return reservationId != null && reservations.remove(reservationId) != null;
    }

    public synchronized int releaseCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return 0;
        }
        int before = reservations.size();
        reservations.values().removeIf(reservation -> capability.equals(reservation.capability()));
        return before - reservations.size();
    }

    public synchronized void replaceCapabilityReservations(
            String capability,
            Collection<AgentInventoryReservation> replacements) {
        if (capability == null || capability.isBlank() || replacements == null
                || replacements.stream().anyMatch(reservation -> reservation == null
                || !capability.equals(reservation.capability()))) {
            throw new IllegalArgumentException("Replacement reservations must share one capability");
        }
        releaseCapability(capability);
        replacements.forEach(reservation -> reservations.put(reservation.reservationId(), reservation));
    }

    public synchronized int reservedQuantity(int itemId, long nowMs) {
        expire(nowMs);
        return reservations.values().stream()
                .filter(reservation -> reservation.itemId() == itemId)
                .mapToInt(AgentInventoryReservation::quantity)
                .sum();
    }

    public synchronized List<AgentInventoryReservation> reservations(long nowMs) {
        expire(nowMs);
        return reservations.values().stream()
                .sorted(Comparator.comparingInt(AgentInventoryReservation::priority).reversed()
                        .thenComparing(AgentInventoryReservation::reservationId))
                .toList();
    }

    public synchronized int expire(long nowMs) {
        int before = reservations.size();
        reservations.values().removeIf(reservation -> reservation.expiresAtMs() > 0
                && nowMs >= reservation.expiresAtMs());
        return before - reservations.size();
    }

    public synchronized void clear() {
        reservations.clear();
    }
}
