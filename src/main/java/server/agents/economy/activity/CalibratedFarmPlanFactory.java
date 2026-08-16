package server.agents.economy.activity;

import server.agents.economy.catalog.EconomyCatalog;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Converts observed rates to explicit work; refuses absent monster or inventory provenance. */
public final class CalibratedFarmPlanFactory {
    private final EconomyCatalog catalog;

    public CalibratedFarmPlanFactory(EconomyCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    public FarmSessionPlan create(String sessionId, String agentId, Instant start, Duration duration,
                                  ActivityCalibration calibration, int dropRateMultiplier,
                                  Set<Integer> activeQuestIds, List<AvailableLot> consumableLots) {
        double minutes = duration.toMillis() / 60_000d;
        int totalKills = (int) Math.floor(calibration.killsPerMinute() * minutes);
        List<FarmSessionPlan.MonsterWork> monsters = allocateKills(totalKills, calibration.monsterKillShare());
        List<FarmSessionPlan.ItemConsumption> consumption = allocateConsumption(
                minutes, calibration.itemUsePerMinute(), consumableLots);
        return new FarmSessionPlan(sessionId, calibration.calibrationId(), agentId,
                calibration.mapId(), start, duration,
                dropRateMultiplier, 0d, Duration.ZERO, monsters, activeQuestIds, consumption);
    }

    private List<FarmSessionPlan.MonsterWork> allocateKills(int total, Map<Integer, Double> shares) {
        List<FarmSessionPlan.MonsterWork> result = new ArrayList<>();
        int allocated = 0;
        List<Integer> ids = new ArrayList<>(shares.keySet());
        Collections.sort(ids);
        for (int index = 0; index < ids.size(); index++) {
            int id = ids.get(index);
            int count = index == ids.size() - 1 ? total - allocated
                    : (int) Math.floor(total * shares.get(id));
            allocated += count;
            if (count > 0) {
                int experience = catalog.monster(id)
                        .orElseThrow(() -> new IllegalStateException("monster catalog missing " + id)).experience();
                result.add(new FarmSessionPlan.MonsterWork(id, count, experience));
            }
        }
        return List.copyOf(result);
    }

    private static List<FarmSessionPlan.ItemConsumption> allocateConsumption(
            double minutes, Map<Integer, Double> rates, List<AvailableLot> lots) {
        Map<Integer, Deque<AvailableLot>> byItem = new HashMap<>();
        for (AvailableLot lot : lots) byItem.computeIfAbsent(lot.itemId(), ignored -> new ArrayDeque<>()).add(lot);
        List<FarmSessionPlan.ItemConsumption> result = new ArrayList<>();
        for (Map.Entry<Integer, Double> rate : rates.entrySet()) {
            int remaining = (int) Math.ceil(rate.getValue() * minutes);
            Deque<AvailableLot> candidates = byItem.getOrDefault(rate.getKey(), new ArrayDeque<>());
            while (remaining > 0 && !candidates.isEmpty()) {
                AvailableLot lot = candidates.removeFirst();
                int quantity = Math.min(remaining, lot.quantity());
                result.add(new FarmSessionPlan.ItemConsumption(lot.itemId(), quantity, lot.lotId()));
                remaining -= quantity;
            }
            if (remaining > 0) throw new IllegalStateException("insufficient calibrated consumable inventory: " + rate.getKey());
        }
        return List.copyOf(result);
    }

    public record AvailableLot(int itemId, int quantity, String lotId) {
        public AvailableLot {
            if (itemId <= 0 || quantity <= 0 || lotId == null || lotId.isBlank()) throw new IllegalArgumentException();
        }
    }
}
