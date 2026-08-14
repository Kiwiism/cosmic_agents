package server.agents.economy.integration.cosmic;

import client.Character;
import client.QuestStatus;
import constants.inventory.ItemConstants;
import server.agents.economy.activity.ActivityCalibration;
import server.agents.economy.activity.ActivityCalibrationRepository;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.VictoriaActivityMapCatalog;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Converts matching real-session calibration evidence into explicit, auditable farm work. */
public final class CalibratedCosmicActivityPlanner implements CosmicEconomyWorldAdapter.ActivityPlanner {
    private final EconomyEngineConfig.Activity config;
    private final ActivityCalibrationRepository calibrations;
    private final VictoriaActivityMapCatalog maps;
    private final EconomyCatalog catalog;

    public CalibratedCosmicActivityPlanner(EconomyEngineConfig.Activity config,
                                           ActivityCalibrationRepository calibrations,
                                           VictoriaActivityMapCatalog maps, EconomyCatalog catalog) {
        this.config = Objects.requireNonNull(config); this.calibrations = Objects.requireNonNull(calibrations);
        this.maps = Objects.requireNonNull(maps); this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public FarmSessionPlan plan(Character agent, EconomyAgentProfile profile, Instant logicalAt) {
        ActivityCalibration calibration = maps.candidates(agent.getLevel()).stream()
                .map(map -> calibrations.find(config.agentBuild, map.mapId(), agent.getLevel(),
                        profile.jobFamily(), config.minimumCalibrationSamples))
                .flatMap(Optional::stream).findFirst()
                .orElseThrow(() -> new MissingActivityCalibrationException("No real activity calibration for build="
                        + config.agentBuild + " job=" + profile.jobFamily() + " level=" + agent.getLevel()));
        int desiredMinutes = Math.max(1, Math.min(config.maximumSessionMinutes,
                (int) Math.round(config.medianSessionMinutes * (.5d + profile.dailyActivityFraction()))));
        int minutes = resourceBoundMinutes(agent, calibration, desiredMinutes);
        if (minutes <= 0) throw new InsufficientCalibratedResourcesException(
                "No calibrated farm minute is supportable by actual consumable holdings for " + profile.agentId());
        Duration duration = Duration.ofMinutes(minutes);
        int totalKills = (int) Math.min(Integer.MAX_VALUE,
                Math.round(calibration.killsPerMinute() * minutes));
        List<FarmSessionPlan.MonsterWork> work = allocateKills(calibration, totalKills);
        List<FarmSessionPlan.ItemConsumption> consumed = consumption(agent, profile, calibration, minutes);
        Set<Integer> quests = new TreeSet<>();
        if (config.objectiveAware) agent.getStartedQuests().forEach(status -> quests.add((int) status.getQuestID()));
        String raw = profile.agentId() + ':' + logicalAt + ':' + calibration.calibrationId();
        String sessionId = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
        return new FarmSessionPlan(sessionId, calibration.calibrationId(), profile.agentId(),
                calibration.mapId(), logicalAt, duration, 1, work, quests, consumed);
    }

    private List<FarmSessionPlan.MonsterWork> allocateKills(ActivityCalibration calibration, int total) {
        List<Allocation> allocations = new ArrayList<>();
        int assigned = 0;
        for (Map.Entry<Integer, Double> entry : new TreeMap<>(calibration.monsterKillShare()).entrySet()) {
            double exact = total * entry.getValue(); int floor = (int) Math.floor(exact); assigned += floor;
            allocations.add(new Allocation(entry.getKey(), floor, exact - floor));
        }
        allocations.sort(Comparator.comparingDouble(Allocation::remainder).reversed()
                .thenComparingInt(Allocation::monsterId));
        for (int i = 0; i < total - assigned && !allocations.isEmpty(); i++)
            allocations.get(i % allocations.size()).kills++;
        return allocations.stream().sorted(Comparator.comparingInt(Allocation::monsterId))
                .map(value -> new FarmSessionPlan.MonsterWork(value.monsterId, value.kills,
                        catalog.monster(value.monsterId).orElseThrow(() ->
                                new IllegalStateException("calibrated monster missing from exact catalog: "
                                        + value.monsterId)).experience())).toList();
    }

    private List<FarmSessionPlan.ItemConsumption> consumption(Character agent, EconomyAgentProfile profile,
                                                               ActivityCalibration calibration, int minutes) {
        List<FarmSessionPlan.ItemConsumption> result = new ArrayList<>();
        new TreeMap<>(calibration.itemUsePerMinute()).forEach((itemId, rate) -> {
            if (!allowedConsumption(itemId)) return;
            int planned = (int) Math.min(Integer.MAX_VALUE, Math.round(rate * minutes));
            int owned = agent.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
            int quantity = planned;
            if (quantity > owned) throw new IllegalStateException("resource-bound duration exceeded holdings");
            if (quantity > 0) result.add(new FarmSessionPlan.ItemConsumption(itemId, quantity,
                    "live-holding:" + profile.agentId() + ':' + itemId));
        });
        return List.copyOf(result);
    }

    private int resourceBoundMinutes(Character agent, ActivityCalibration calibration, int desiredMinutes) {
        int supported = desiredMinutes;
        for (Map.Entry<Integer, Double> use : calibration.itemUsePerMinute().entrySet()) {
            if (!allowedConsumption(use.getKey()) || use.getValue() <= 0) continue;
            int owned = agent.getInventory(ItemConstants.getInventoryType(use.getKey())).countById(use.getKey());
            supported = Math.min(supported, (int) Math.floor(owned / use.getValue()));
        }
        return supported;
    }

    private boolean allowedConsumption(int itemId) {
        if (ItemConstants.isThrowingStar(itemId) || ItemConstants.isBullet(itemId) || ItemConstants.isArrow(itemId))
            return config.consumeAmmunition;
        if (ItemConstants.isPotion(itemId) || ItemConstants.isFood(itemId))
            return config.consumeHpPotions || config.consumeMpPotions;
        return false;
    }

    private static final class Allocation {
        private final int monsterId; private int kills; private final double remainder;
        private Allocation(int monsterId, int kills, double remainder) {
            this.monsterId = monsterId; this.kills = kills; this.remainder = remainder;
        }
        int monsterId() { return monsterId; } double remainder() { return remainder; }
    }

    public static final class MissingActivityCalibrationException extends IllegalStateException {
        public MissingActivityCalibrationException(String message) { super(message); }
    }
    public static final class InsufficientCalibratedResourcesException extends IllegalStateException {
        public InsufficientCalibratedResourcesException(String message) { super(message); }
    }
}
