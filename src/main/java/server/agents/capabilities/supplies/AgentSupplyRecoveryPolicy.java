package server.agents.capabilities.supplies;

import client.Character;
import client.Job;
import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.progression.AgentVictoriaTrainingCatalog;
import server.agents.progression.AgentVictoriaTrainingCatalogRepository;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Bounded economic and safety policy for autonomous resupply recovery. */
public final class AgentSupplyRecoveryPolicy {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.supplies.AgentSupplyRecoveryPolicy.";
    private static final int WALLET_RESERVE_BASE = tuningInt("WALLET_RESERVE_BASE");
    private static final int WALLET_RESERVE_PER_LEVEL = tuningInt("WALLET_RESERVE_PER_LEVEL");
    private static final int MINIMUM_INCOME_TARGET = tuningInt("MINIMUM_INCOME_TARGET");
    private static final int MAXIMUM_INCOME_TARGET = tuningInt("MAXIMUM_INCOME_TARGET");
    private static final int MAX_RECOVERY_ATTEMPTS = tuningInt("MAX_RECOVERY_ATTEMPTS");
    private static final int REST_TARGET_PERCENT = tuningInt("REST_TARGET_PERCENT");
    private static final int CRITICAL_HP_PERCENT = tuningInt("CRITICAL_HP_PERCENT");
    private static final long REST_TIMEOUT_MS = tuningLong("REST_TIMEOUT_MS");
    private static final long INCOME_TIMEOUT_MS = tuningLong("INCOME_TIMEOUT_MS");

    private AgentSupplyRecoveryPolicy() {
    }

    public static int minimumWalletReserve(Character agent) {
        int level = agent == null ? 1 : Math.max(1, agent.getLevel());
        return Math.max(0, WALLET_RESERVE_BASE + level * WALLET_RESERVE_PER_LEVEL);
    }

    /**
     * Beginners are intentionally viable without a potion reserve. Their early progression
     * relies on passive recovery and combat safety, and there is no useful income-recovery
     * route before first-job advancement. Treating an empty potion slot as an emergency here
     * would suspend the very progression that unlocks normal resupply.
     */
    public static boolean requiresAutomaticReserve(
            Character agent, AgentResourceCategory category) {
        if (agent == null || category == null) {
            return false;
        }
        boolean potion = category == AgentResourceCategory.HP_POTION
                || category == AgentResourceCategory.MP_POTION;
        return !potion || agent.getJob() != Job.BEGINNER;
    }

    public static int recoveryMesoTarget(Character agent, AgentProcurementRequest request) {
        long requested = request == null ? MINIMUM_INCOME_TARGET : request.maximumBudget();
        int spend = (int) Math.clamp(requested,
                MINIMUM_INCOME_TARGET, MAXIMUM_INCOME_TARGET);
        return Math.addExact(minimumWalletReserve(agent), spend);
    }

    public static boolean recoveredForCombat(Character agent) {
        if (agent == null) return false;
        return percent(agent.getHp(), agent.getCurrentMaxHp()) >= REST_TARGET_PERCENT
                && percent(agent.getMp(), agent.getCurrentMaxMp()) >= REST_TARGET_PERCENT;
    }

    public static boolean criticallyLowHp(Character agent) {
        return agent == null
                || percent(agent.getHp(), agent.getCurrentMaxHp()) < CRITICAL_HP_PERCENT;
    }

    public static int maximumRecoveryAttempts() {
        return MAX_RECOVERY_ATTEMPTS;
    }

    public static long restTimeoutMs() {
        return REST_TIMEOUT_MS;
    }

    public static long incomeTimeoutMs() {
        return INCOME_TIMEOUT_MS;
    }

    public static Optional<RecoveryMap> selectRecoveryMap(Character agent) {
        if (agent == null || agent.getJob().getId() == 0 || agent.getLevel() < 15) {
            return Optional.empty();
        }
        return AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog()
                .trainingMaps().stream()
                .filter(map -> map.recommendedMinLevel() <= agent.getLevel())
                .filter(map -> map.tags().stream().anyMatch("safe"::equalsIgnoreCase))
                .filter(map -> map.spawns().stream().allMatch(
                        spawn -> spawn.mobLevel() <= Math.max(1, agent.getLevel() - 3)))
                .filter(map -> !targets(map).isEmpty())
                .map(map -> new RecoveryMap(map.mapId(), targets(map), score(map, agent.getLevel())))
                .max(Comparator.comparingLong(RecoveryMap::score));
    }

    public static Optional<RecoveryMap> recoveryMap(int mapId, int level) {
        return AgentVictoriaTrainingCatalogRepository.defaultRepository().findMap(mapId)
                .filter(map -> map.tags().stream().anyMatch("safe"::equalsIgnoreCase))
                .filter(map -> !targets(map).isEmpty())
                .map(map -> new RecoveryMap(map.mapId(), targets(map), score(map, level)));
    }

    private static Set<Integer> targets(AgentVictoriaTrainingCatalog.TrainingMap map) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        map.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role()))
                .forEach(spawn -> result.add(spawn.mobId()));
        return Set.copyOf(result);
    }

    private static long score(AgentVictoriaTrainingCatalog.TrainingMap map, int level) {
        int spawnCount = map.spawns().stream()
                .mapToInt(AgentVictoriaTrainingCatalog.SpawnGroup::expectedCount).sum();
        int maximumMobLevel = map.spawns().stream()
                .mapToInt(AgentVictoriaTrainingCatalog.SpawnGroup::mobLevel).max().orElse(level);
        long lowRisk = Math.max(0, level - maximumMobLevel) * 100L;
        long density = spawnCount * 25L;
        long hazardPenalty = map.hazards().size() * 75L;
        return lowRisk + density - hazardPenalty;
    }

    private static int percent(int current, int maximum) {
        return maximum <= 0 ? 100 : Math.clamp(current * 100 / maximum, 0, 100);
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }

    public record RecoveryMap(int mapId, Set<Integer> mobIds, long score) {
        public RecoveryMap {
            if (mapId <= 0 || mobIds == null || mobIds.isEmpty()) {
                throw new IllegalArgumentException("a recovery map requires targets");
            }
            mobIds = Set.copyOf(mobIds);
        }
    }
}
