package server.agents.field;

import client.Character;
import client.BuffStat;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.StatEffect;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.AgentBuildStateRuntime;
import server.agents.capabilities.build.AgentStarterKitService;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.VictoriaFirstJobMvpTestService;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.LifeFactory;
import server.life.Monster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import tools.Pair;

/** Deterministically resets a dedicated pooled Agent for a long observation run. */
final class AgentFieldObservationFixtureService {
    private static final int POWER_ELIXIR_ITEM_ID = 2_000_005;
    private static final int ALL_CURE_ITEM_ID = 2_050_004;
    private static final int BOW_ARROW_ITEM_ID = 2_060_000;
    private static final int CROSSBOW_ARROW_ITEM_ID = 2_061_000;
    private static final int THROWING_STAR_ITEM_ID = 2_070_000;
    private static final int BULLET_ITEM_ID = 2_330_000;
    private static final int SNIPER_PILL_ITEM_ID = 2_002_008;
    private static final int KPQ_ACCURACY_HEADBAND_ITEM_ID = 1_002_014;
    private static final int KPQ_FISH_SPEAR_ITEM_ID = 1_432_008;
    private static final int MAPLE_SHIELD_ITEM_ID = 1_092_030;
    private static final Set<Integer> COMMON_MAGE_DAGGER_SHIELDS = Set.of(1_092_003, 1_092_008);
    private static final Set<Integer> WARRIOR_SHIELDS = Set.of(1_092_000, 1_092_001, 1_092_005, 1_092_006);
    private static final Set<Integer> THIEF_WRISTGUARDS = Set.of(1_092_018, 1_092_019, 1_092_020);
    private static final short POWER_ELIXIRS = 2_000;
    private static final short ALL_CURES = 500;
    private static final short SNIPER_PILLS = 100;
    private static final short PROJECTILES = 30_000;
    private static final byte OBSERVATION_USE_SLOTS = 96;
    private static final double MINIMUM_ACCEPTABLE_HIT_CHANCE = config.AgentTuning.doubleValue(
            "server.agents.field.AgentFieldObservationFixtureService.MINIMUM_ACCEPTABLE_HIT_CHANCE");
    private static final double MINIMUM_FALLBACK_HIT_CHANCE = config.AgentTuning.doubleValue(
            "server.agents.field.AgentFieldObservationFixtureService.MINIMUM_FALLBACK_HIT_CHANCE");
    private static final int OBSERVATION_LEVEL_CAP = config.AgentTuning.intValue(
            "server.agents.field.AgentFieldObservationFixtureService.OBSERVATION_LEVEL_CAP");
    private static final int OBSERVATION_LEVEL_CAP_KPQ = config.AgentTuning.intValue(
            "server.agents.field.AgentFieldObservationFixtureService.OBSERVATION_LEVEL_CAP_KPQ");
    private static final List<String> CAREERS = List.of(
            "warrior", "bowman", "magician", "thief-claw", "thief-dagger",
            "pirate-gun", "pirate-knuckle");
    private static final String MAGICIAN_JOB_STYLE = "MAGICIAN";

    private AgentFieldObservationFixtureService() {
    }

    static Prepared prepare(AgentRuntimeEntry entry, int level, long seed, long nowMs) throws IOException {
        return prepare(entry, level, Set.<Integer>of(), seed, nowMs, OBSERVATION_LEVEL_CAP,
                MINIMUM_ACCEPTABLE_HIT_CHANCE, MINIMUM_ACCEPTABLE_HIT_CHANCE, CAREERS, false);
    }

    static Prepared prepareForKpq(AgentRuntimeEntry entry, int level, long seed, long nowMs) throws IOException {
        return prepareForKpq(entry, level, Set.<Integer>of(), seed, nowMs);
    }

    static Prepared prepareForKpq(AgentRuntimeEntry entry,
                                  int level,
                                  Set<Integer> allowedMobIds,
                                  long seed,
                                  long nowMs)
            throws IOException {
        return prepare(entry, level, allowedMobIds, seed, nowMs, OBSERVATION_LEVEL_CAP_KPQ,
                MINIMUM_ACCEPTABLE_HIT_CHANCE, MINIMUM_FALLBACK_HIT_CHANCE, CAREERS, true);
    }

    static Prepared prepareForKpq(AgentRuntimeEntry entry,
                                  int level,
                                  Set<Integer> allowedMobIds,
                                  String career,
                                  long seed,
                                  long nowMs)
            throws IOException {
        if (!CAREERS.contains(career)) {
            throw new IllegalArgumentException("unknown observation career: " + career);
        }
        return prepare(entry, level, allowedMobIds, seed, nowMs, OBSERVATION_LEVEL_CAP_KPQ,
                MINIMUM_ACCEPTABLE_HIT_CHANCE, MINIMUM_FALLBACK_HIT_CHANCE, List.of(career), true);
    }

    static Prepared prepareForBalrog(AgentRuntimeEntry entry,
                                     AgentBalrogTestFixtureService.Build build,
                                     int level,
                                     Set<Integer> allowedMobIds,
                                     long nowMs) throws IOException {
        if (build == null || !CAREERS.contains(build.career()) || level < 30 || level >= 70) {
            throw new IllegalArgumentException("a known weapon build and level 30-69 are required");
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            throw new IllegalArgumentException("a spawned Agent is required");
        }

        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                entry, build.career(), "lv10", VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "Balrog observation fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);
        while (agent.getLevel() < 30) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        entry.apBuildProfileState().clear();
        entry.spBuildProfileState().clear();
        AgentBuildStateRuntime.setApBuild(entry, build.apBuild());
        AgentStarterKitService.advanceJob(entry, build.job(), build.spBuild());
        while (agent.getLevel() < level) {
            agent.levelUp(false);
            AgentBuildService.autoAssignAp(entry, agent);
            AgentBuildService.autoAssignSp(entry, agent, build.spBuild());
        }
        AgentBuildService.autoAssignAp(entry, agent);
        AgentBuildService.autoAssignSp(entry, agent, build.spBuild());

        List<Integer> equipment = applyBalrogEquipmentLoadout(agent, build);
        provisionTwoHourSupplies(agent);
        int accuracyBonus = sniperPillBonus();
        if (accuracyBonus > 0) {
            applySniperPills(agent, 1);
        }
        double hitChance = minimumHitChance(agent, allowedMobIds, accuracyBonus);
        Item weapon = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        int weaponItemId = weapon == null ? 0 : weapon.getItemId();
        int weaponAttack = weapon instanceof Equip equip ? Math.max(equip.getWatk(), equip.getMatk()) : 0;
        int remainingAp = Math.max(0, agent.getRemainingAp());
        int[] remainingSps = agent.getRemainingSps().clone();
        agent.healHpMp();
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), level, build.job().name().toLowerCase(), bundle.bundleId(),
                bundle.apProfileId(), bundle.spProfileId(), equipment, suppliedProjectile(agent), hitChance,
                remainingAp == 0 && Arrays.stream(remainingSps).allMatch(sp -> sp <= 0),
                remainingAp, remainingSps, weaponItemId, weaponAttack);
    }

    private static List<Integer> applyBalrogEquipmentLoadout(
            Character agent, AgentBalrogTestFixtureService.Build build) {
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        clearInventory(agent, InventoryType.EQUIP, agent.getInventory(InventoryType.EQUIP).getSlotLimit());
        clearInventory(agent, InventoryType.EQUIPPED, agent.getInventory(InventoryType.EQUIPPED).getSlotLimit());
        List<Integer> selected = build.equipment(agent.getGender());
        HashSet<String> occupiedSlots = new HashSet<>();
        for (int itemId : selected) {
            Equip equip = inventory.getEquipById(itemId);
            int requiredLevel = inventory.getEquipLevelRequirement(itemId);
            if (equip == null || requiredLevel > agent.getLevel()
                    || (itemId == build.weaponItemId()
                    && requiredLevel < AgentBalrogTestFixtureService.MINIMUM_WEAPON_LEVEL)
                    || !genderCompatible(agent.getGender(), itemId)
                    || !inventory.meetsEquipRequirements(equip, agent.getJob(), agent.getLevel(),
                    agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) {
                throw new IllegalStateException("Balrog build cannot legally equip item " + itemId);
            }
            String slot = normalizedSlot(inventory.getEquipmentSlot(itemId));
            if (slot.isEmpty() || !occupiedSlots.add(slot)) {
                throw new IllegalStateException("Balrog build has an invalid or duplicate slot for " + itemId);
            }
            if (!inventory.addItem(agent, itemId, (short) 1)) {
                throw new IllegalStateException("could not add Balrog equipment " + itemId);
            }
        }
        equipSelectedLoadout(agent, inventory, selected);
        Item weapon = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null || weapon.getItemId() != build.weaponItemId()) {
            throw new IllegalStateException("Balrog build did not equip its selected weapon");
        }
        return selected;
    }

    static Prepared prepare(AgentRuntimeEntry entry, int level, Set<Integer> allowedMobIds, long seed, long nowMs)
            throws IOException {
        return prepare(entry, level, allowedMobIds, seed, nowMs, level,
                MINIMUM_ACCEPTABLE_HIT_CHANCE, MINIMUM_ACCEPTABLE_HIT_CHANCE, CAREERS, false);
    }

    private static Prepared prepare(
            AgentRuntimeEntry entry,
            int level,
            Set<Integer> allowedMobIds,
            long seed,
            long nowMs,
            int maxLevelCap,
            double minimumHitChance,
            double fallbackHitChance,
            List<String> careers,
            boolean kpqLoadout)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || level < 15 || level > 28) {
            throw new IllegalArgumentException("a spawned Agent and level 15-28 are required");
        }
        if (maxLevelCap < level) {
            throw new IllegalArgumentException("requested maximum level cannot be lower than start level");
        }
        if (minimumHitChance < 0.0d || minimumHitChance > 1.0d
                || fallbackHitChance < 0.0d || fallbackHitChance > 1.0d) {
            throw new IllegalArgumentException("minimum hit chance must be in the 0.0-1.0 range");
        }
        if (fallbackHitChance > minimumHitChance) {
            throw new IllegalArgumentException("fallback hit chance must not exceed primary hit chance");
        }
        Set<Integer> allowed = allowedMobIds == null || allowedMobIds.isEmpty()
                ? Set.of()
                : Set.copyOf(allowedMobIds);

        ObservationCandidate bestMeetingThreshold = null;
        int cappedMaxLevel = Math.min(maxLevelCap, OBSERVATION_LEVEL_CAP_KPQ);
        ObservationCandidate bestMeetingFallback = null;
        int startOffset = Math.floorMod(seed, careers.size());
        for (int index = 0; index < careers.size(); index++) {
            String career = careers.get((index + startOffset) % careers.size());
            long careerSeed = mix(seed, index);
            ObservationCandidate candidate = evaluateCareer(
                    entry, career, level, cappedMaxLevel, allowed, minimumHitChance,
                    fallbackHitChance, careerSeed, (index + startOffset), kpqLoadout);
            if (candidate == null) {
                continue;
            }
            if (candidate.meetsAccuracyMinimum() && bestMeetingThreshold == null) {
                // Career iteration begins at a seed-selected offset. Keep the first legal build
                // that meets the target instead of collapsing every KPQ fixture to whichever
                // career happens to produce the numerically highest hit chance.
                bestMeetingThreshold = candidate;
            } else if (candidate.minimumHitChance() >= fallbackHitChance) {
                bestMeetingFallback = betterCandidate(candidate, bestMeetingFallback) ? candidate : bestMeetingFallback;
            }
        }
        ObservationCandidate selected = bestMeetingThreshold != null ? bestMeetingThreshold : bestMeetingFallback;
        if (selected == null) {
            if (minimumHitChance == fallbackHitChance) {
                throw new IllegalStateException("observation fixture has no setup that meets the required "
                        + Math.round(minimumHitChance * 100) + "% threshold");
            }
            throw new IllegalStateException("observation fixture has no setup that meets the required "
                    + Math.round(minimumHitChance * 100) + "% threshold and no fallback setup >= "
            + Math.round(fallbackHitChance * 100) + "%");
        }
        return finalizeCandidate(entry, selected, nowMs, kpqLoadout);
    }

    private static List<Integer> applyDeterministicEquipmentLoadout(
            Character agent, long seed, String career, boolean kpqLoadout) {
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        clearInventory(agent, InventoryType.EQUIP, agent.getInventory(InventoryType.EQUIP).getSlotLimit());
        clearInventory(agent, InventoryType.EQUIPPED, agent.getInventory(InventoryType.EQUIPPED).getSlotLimit());
        Map<String, List<Integer>> candidatesBySlot = new LinkedHashMap<>();
        for (int itemId : equipmentItemIdsForCareer(career)) {
            Equip equip = inventory.getEquipById(itemId);
            if (equip == null || inventory.getEquipLevelRequirement(itemId) > agent.getLevel()
                    || !genderCompatible(agent.getGender(), itemId)
                    || !inventory.meetsEquipRequirements(equip, agent.getJob(), agent.getLevel(),
                    agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) {
                continue;
            }
            WeaponType weaponType = inventory.getWeaponType(itemId);
            if (weaponType != null && weaponType != WeaponType.NOT_A_WEAPON
                    && !weaponCompatible(agent, weaponType, career)) {
                continue;
            }
            String slot = normalizedSlot(inventory.getEquipmentSlot(itemId));
            if ("Si".equals(slot) && !shieldAllowedForCareer(itemId, career)) {
                continue;
            }
            if (!slot.isEmpty()) {
                candidatesBySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(itemId);
            }
        }
        SplittableRandom random = new SplittableRandom(seed);
        ArrayList<Integer> selected = new ArrayList<>();
        boolean overall = random.nextBoolean() && candidatesBySlot.containsKey("MaPn");
        List<String> slots = overall
                ? List.of("Cp", "MaPn", "So", "Gv", "Ae")
                : List.of("Cp", "Ma", "Pn", "So", "Gv", "Ae");
        for (String slot : slots) {
            selectOne(agent, inventory, candidatesBySlot, slot, random, false,
                    kpqLoadout ? kpqPreferredEquipmentId(career, slot) : 0, selected);
        }
        int weaponItemId = selectOne(agent, inventory, candidatesBySlot, "Wp", random, true,
                kpqLoadout ? kpqPreferredEquipmentId(career, "Wp") : 0, selected);
        if (weaponItemId == 0) {
            throw new IllegalStateException("deterministic observation preset has no compatible weapon");
        }
        WeaponType weaponType = inventory.getWeaponType(weaponItemId);
        if (supportsShield(weaponType, inventory.getEquipmentSlot(weaponItemId))) {
            selectOne(agent, inventory, candidatesBySlot, "Si", random, false, 0, selected);
        }
        equipSelectedLoadout(agent, inventory, selected);
        Item weapon = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            throw new IllegalStateException("deterministic observation preset has no compatible weapon");
        }
        if (agent.getInventory(InventoryType.EQUIPPED).getItem((short) -5) == null
                || (!overall && agent.getInventory(InventoryType.EQUIPPED).getItem((short) -6) == null)) {
            throw new IllegalStateException("deterministic observation preset has no complete outfit");
        }
        return List.copyOf(selected);
    }

    private static Prepared finalizeCandidate(
            AgentRuntimeEntry entry, ObservationCandidate selected, long nowMs, boolean kpqLoadout)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            throw new IllegalArgumentException("a spawned Agent and level 15-25 are required");
        }
        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                entry, selected.career(), "lv10", VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "field observation fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);
        while (agent.getLevel() < selected.level()) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        if (agent.getLevel() != selected.level()) {
            throw new IllegalStateException("pooled fixture is already above requested level " + selected.level());
        }
        AgentApBuildProfileService.autoAssign(entry, agent);
        AgentSpBuildProfileService.autoAssign(entry, agent);

        List<Integer> equipment = applyDeterministicEquipmentLoadout(
                agent, selected.loadoutSeed(), selected.career(), kpqLoadout);
        Item equippedWeaponItem = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        int weaponItemId = equippedWeaponItem == null ? 0 : equippedWeaponItem.getItemId();
        int weaponAttack = equippedWeaponItem instanceof Equip equip
                ? Math.max(equip.getWatk(), equip.getMatk()) : 0;
        provisionTwoHourSupplies(agent);
        if (selected.sniperPillsNeeded() >= 0 && selected.sniperPillsNeeded() <= SNIPER_PILLS) {
            applySniperPills(agent, selected.sniperPillsNeeded());
        }
        int remainingAp = Math.max(0, agent.getRemainingAp());
        int[] remainingSps = agent.getRemainingSps().clone();
        agent.healHpMp();
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), selected.level(), selected.career(), bundle.bundleId(), bundle.apProfileId(),
                bundle.spProfileId(), equipment, suppliedProjectile(agent), selected.minimumHitChance(),
                remainingAp == 0 && Arrays.stream(remainingSps).allMatch(sp -> sp <= 0),
                remainingAp, remainingSps, weaponItemId, weaponAttack);
    }

    private static ObservationCandidate evaluateCareer(
            AgentRuntimeEntry entry,
            String career,
            int level,
            int maxLevel,
            Set<Integer> allowedMobIds,
            double minimumHitChanceThreshold,
            double fallbackHitChanceThreshold,
            long seed,
            int careerOrder,
            boolean kpqLoadout) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            throw new IllegalArgumentException("a spawned Agent is required");
        }
        VictoriaFirstJobMvpTestService.resetAndStart(entry, career, "lv10",
                VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, 0L);
        AgentUniversalPlanRuntime.cancel(entry, agent, "field observation fixture", 0L);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);

        ObservationCandidate bestForCareer = null;
        int bonusPerPill = sniperPillBonus();
        for (int candidateLevel = level; candidateLevel <= maxLevel; candidateLevel++) {
            while (agent.getLevel() < candidateLevel) {
                agent.levelUp(false);
                AgentApBuildProfileService.autoAssign(entry, agent);
                AgentSpBuildProfileService.autoAssign(entry, agent);
            }
            if (agent.getLevel() != candidateLevel) {
                continue;
            }
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);

            long loadoutSeed = mix(seed, candidateLevel);
            try {
                applyDeterministicEquipmentLoadout(agent, loadoutSeed, career, kpqLoadout);
            } catch (IllegalStateException ignored) {
                continue;
            }
            int requiredPills = requiredSniperPills(agent, allowedMobIds, bonusPerPill, minimumHitChanceThreshold);
            boolean meetsPrimaryThreshold = requiredPills != Integer.MAX_VALUE;
            if (!meetsPrimaryThreshold) {
                requiredPills = requiredSniperPills(agent, allowedMobIds, bonusPerPill, fallbackHitChanceThreshold);
                if (requiredPills == Integer.MAX_VALUE) {
                    continue;
                }
            }
            double candidateMinimumHitChance = minimumHitChance(agent, allowedMobIds, requiredPills * bonusPerPill);
            ObservationCandidate current = new ObservationCandidate(
                    career, candidateLevel, loadoutSeed, requiredPills, candidateMinimumHitChance,
                    meetsPrimaryThreshold, careerOrder);
            if (betterCandidate(current, bestForCareer)) {
                bestForCareer = current;
            }
        }
        return bestForCareer;
    }

    private static boolean betterCandidate(ObservationCandidate candidate, ObservationCandidate best) {
        if (best == null) {
            return true;
        }
        if (candidate.meetsAccuracyMinimum() != best.meetsAccuracyMinimum()) {
            return candidate.meetsAccuracyMinimum();
        }
        if (candidate.minimumHitChance() != best.minimumHitChance()) {
            return candidate.minimumHitChance() > best.minimumHitChance();
        }
        if (candidate.sniperPillsNeeded() != best.sniperPillsNeeded()) {
            return candidate.sniperPillsNeeded() < best.sniperPillsNeeded();
        }
        if (candidate.level() != best.level()) {
            return candidate.level() > best.level();
        }
        return candidate.careerOrder() < best.careerOrder();
    }

    /**
     * Observation fixtures select a legal visual loadout, not a damage-maximizing plan. Sending
     * the selection back through the generic optimizer allowed zero-stat shirts and pants to lose
     * to its empty-slot option after the fixture had already cleared the default clothes.
     */
    private static void equipSelectedLoadout(Character agent,
                                              InventoryGateway inventory,
                                              List<Integer> selected) {
        for (int itemId : selected) {
            short equippedSlot = equippedSlot(normalizedSlot(inventory.getEquipmentSlot(itemId)));
            if (equippedSlot == 0) {
                continue;
            }
            Item item = agent.getInventory(InventoryType.EQUIP).list().stream()
                    .filter(candidate -> candidate.getItemId() == itemId && candidate.getPosition() > 0)
                    .findFirst().orElse(null);
            if (item == null) {
                throw new IllegalStateException("selected observation equipment is missing " + itemId);
            }
            inventory.moveItem(agent, InventoryType.EQUIP, item.getPosition(), equippedSlot, (short) 1);
        }
    }

    static short equippedSlot(String normalizedSlot) {
        return switch (normalizedSlot) {
            case "Cp" -> -1;
            case "Ae" -> -4;
            case "Ma", "MaPn" -> -5;
            case "Pn" -> -6;
            case "So" -> -7;
            case "Gv" -> -8;
            case "Si" -> -10;
            case "Wp" -> -11;
            default -> 0;
        };
    }

    private static int selectOne(Character agent,
                                 InventoryGateway inventory,
                                 Map<String, List<Integer>> candidatesBySlot,
                                 String slot,
                                 SplittableRandom random,
                                 boolean preferAccuracy,
                                 int preferredItemId,
                                 List<Integer> selected) {
        List<Integer> candidates = candidatesBySlot.getOrDefault(slot, List.of());
        if (candidates.isEmpty()) {
            return 0;
        }
        List<Integer> sortedCandidates = candidates.stream()
                .sorted(preferAccuracy ? candidateWeaponComparator(inventory) : Comparator.naturalOrder())
                .toList();
        int itemId;
        if (preferredItemId > 0 && candidates.contains(preferredItemId)) {
            itemId = preferredItemId;
        } else if (preferAccuracy) {
            itemId = sortedCandidates.get(0);
        } else {
            itemId = sortedCandidates.get(random.nextInt(sortedCandidates.size()));
        }
        if (!inventory.addItem(agent, itemId, (short) 1)) {
            throw new IllegalStateException("could not add observation equipment " + itemId);
        }
        selected.add(itemId);
        return itemId;
    }

    static int kpqPreferredEquipmentId(String career, String slot) {
        if ("warrior".equals(career) && "Cp".equals(slot)) {
            return KPQ_ACCURACY_HEADBAND_ITEM_ID;
        }
        if (("warrior".equals(career) || "pirate-knuckle".equals(career)) && "Wp".equals(slot)) {
            return KPQ_FISH_SPEAR_ITEM_ID;
        }
        return 0;
    }

    private static List<Integer> equipmentItemIdsForCareer(String career) {
        if (!"thief-dagger".equals(career)) {
            return AgentFieldObservationEquipmentRepository.itemIds();
        }
        return java.util.stream.Stream.concat(
                        AgentFieldObservationEquipmentRepository.itemIds().stream(),
                        THIEF_WRISTGUARDS.stream())
                .distinct().toList();
    }

    static boolean shieldAllowedForCareer(int itemId, String career) {
        if (itemId == MAPLE_SHIELD_ITEM_ID) {
            return false;
        }
        return switch (career) {
            case "magician" -> COMMON_MAGE_DAGGER_SHIELDS.contains(itemId);
            case "thief-dagger" -> COMMON_MAGE_DAGGER_SHIELDS.contains(itemId)
                    || THIEF_WRISTGUARDS.contains(itemId);
            case "warrior" -> WARRIOR_SHIELDS.contains(itemId);
            default -> false;
        };
    }

    static Comparator<Integer> candidateWeaponComparator(InventoryGateway inventory) {
        return (left, right) -> {
            int leftAcc = weaponOrEquipAccuracy(inventory, left);
            int rightAcc = weaponOrEquipAccuracy(inventory, right);
            int sort = Integer.compare(rightAcc, leftAcc);
            if (sort != 0) {
                return sort;
            }
            int leftPower = weaponOrEquipPower(inventory, left);
            int rightPower = weaponOrEquipPower(inventory, right);
            sort = Integer.compare(rightPower, leftPower);
            if (sort != 0) {
                return sort;
            }
            return Integer.compare(left, right);
        };
    }

    private static int weaponOrEquipAccuracy(InventoryGateway inventory, int itemId) {
        Equip equip = inventory.getEquipById(itemId);
        return equip == null ? 0 : equip.getAcc();
    }

    private static int weaponOrEquipPower(InventoryGateway inventory, int itemId) {
        Equip equip = inventory.getEquipById(itemId);
        return equip == null ? 0 : Math.max(equip.getWatk(), equip.getMatk());
    }

    private static int sniperPillBonus() {
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        StatEffect sniperPill = inventory.getItemEffect(SNIPER_PILL_ITEM_ID);
        if (sniperPill == null) {
            return 0;
        }
        for (Pair<BuffStat, Integer> stat : sniperPill.getStatups()) {
            if (stat.getLeft() == BuffStat.ACC) {
                return Math.max(0, stat.getRight());
            }
        }
        return 0;
    }

    private static int requiredSniperPills(Character agent, Set<Integer> allowedMobIds, int bonusPerPill,
                                           double minimumHitChance) {
        if (minimumHitChance(agent, allowedMobIds, 0) >= minimumHitChance) return 0;
        if (bonusPerPill > 0
                && minimumHitChance(agent, allowedMobIds, bonusPerPill) >= minimumHitChance) return 1;
        return Integer.MAX_VALUE;
    }

    private static double minimumHitChance(Character agent, Set<Integer> allowedMobIds, int bonusAccuracy) {
        if (allowedMobIds == null || allowedMobIds.isEmpty()) {
            return 1.0;
        }
        CombatFormulaProvider combatFormula = CombatFormulaProvider.getInstance();
        boolean isMage = agent.getJobStyle().name().equals(MAGICIAN_JOB_STYLE);
        double minimum = 1.0;
        for (int mobId : allowedMobIds) {
            Monster mob = LifeFactory.getMonster(mobId);
            if (mob == null) {
                continue;
            }
            double hit = isMage
                    ? combatFormula.calculateMobHitChance(agent, mob, true)
                    : combatFormula.calculatePhysicalMobHitChance(
                    combatFormula.getTotalAccuracy(agent) + bonusAccuracy, agent.getLevel(),
                    mob.getLevel(), mob.getAvoidability());
            minimum = Math.min(minimum, hit);
        }
        return minimum;
    }

    private static void applySniperPills(Character agent, int requiredPills) {
        for (int used = 0; used < requiredPills; used++) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().useItem(agent, SNIPER_PILL_ITEM_ID);
        }
    }

    private static void provisionTwoHourSupplies(Character agent) {
        clearInventory(agent, InventoryType.USE,
                (byte) Math.max(Byte.toUnsignedInt(agent.getInventory(InventoryType.USE).getSlotLimit()),
                        Byte.toUnsignedInt(OBSERVATION_USE_SLOTS)));
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        require(inventory.addItem(agent, POWER_ELIXIR_ITEM_ID, POWER_ELIXIRS), "Power Elixirs");
        require(inventory.addItem(agent, ALL_CURE_ITEM_ID, ALL_CURES), "All Cure Potions");
        require(inventory.addItem(agent, SNIPER_PILL_ITEM_ID, SNIPER_PILLS), "Sniper Pills");
        WeaponType weapon = equippedWeaponType(agent, inventory);
        int projectile = switch (weapon) {
            case BOW -> BOW_ARROW_ITEM_ID;
            case CROSSBOW -> CROSSBOW_ARROW_ITEM_ID;
            case CLAW -> THROWING_STAR_ITEM_ID;
            case GUN -> BULLET_ITEM_ID;
            default -> 0;
        };
        if (projectile > 0) {
            require(inventory.addItem(agent, projectile, PROJECTILES), "two-hour projectile reserve");
        }
    }

    private static WeaponType equippedWeaponType(Character agent, InventoryGateway inventory) {
        Item weapon = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        return weapon == null ? WeaponType.NOT_A_WEAPON : inventory.getWeaponType(weapon.getItemId());
    }

    private static int suppliedProjectile(Character agent) {
        return switch (equippedWeaponType(agent, AgentInventoryGatewayRuntime.inventory())) {
            case BOW -> BOW_ARROW_ITEM_ID;
            case CROSSBOW -> CROSSBOW_ARROW_ITEM_ID;
            case CLAW -> THROWING_STAR_ITEM_ID;
            case GUN -> BULLET_ITEM_ID;
            default -> 0;
        };
    }

    private static void clearInventory(Character agent, InventoryType type, byte slotLimit) {
        agent.setInventory(type, new Inventory(agent, type, slotLimit));
    }

    static String normalizedSlot(String slot) {
        if (slot == null) {
            return "";
        }
        if (slot.equals("Wp") || slot.equals("WpSi")) {
            return "Wp";
        }
        return switch (slot) {
            case "Cp", "Ma", "Pn", "MaPn", "So", "Gv", "Ae", "Si" -> slot;
            default -> "";
        };
    }

    static boolean supportsShield(WeaponType weaponType, String equipmentSlot) {
        if (equipmentSlot == null || equipmentSlot.contains("Si")) {
            return false;
        }
        return switch (weaponType) {
            case GENERAL1H_SWING, GENERAL1H_STAB, SWORD1H,
                    DAGGER_THIEVES, DAGGER_OTHER, WAND, STAFF -> true;
            default -> false;
        };
    }

    static boolean weaponCompatible(Character agent, WeaponType weaponType, String career) {
        if (agent.getJob() != Job.WARRIOR) {
            if (AgentEquipmentService.isWeaponCompatible(agent, weaponType)) {
                return true;
            }
            // The level-20 Fish Spear is an all-job accuracy weapon. The KPQ knuckle
            // fixture uses weapon-neutral Somersault Kick, so retaining this narrow
            // career exception is both legal and avoids inventing stacked pill buffs.
            if ("pirate-knuckle".equals(career)
                    && switch (weaponType) {
                        case SPEAR_STAB, SPEAR_SWING -> true;
                        default -> false;
                    }) {
                return true;
            }
            return false;
        }
        return switch (weaponType) {
            case SWORD1H, SWORD2H, GENERAL1H_SWING, GENERAL1H_STAB,
                    GENERAL2H_SWING, GENERAL2H_STAB, SPEAR_STAB, SPEAR_SWING,
                    POLE_ARM_STAB, POLE_ARM_SWING -> true;
            default -> false;
        };
    }

    private static boolean genderCompatible(int gender, int itemId) {
        int family = itemId / 1_000;
        if (gender == 0) {
            return family != 1_041 && family != 1_051 && family != 1_061;
        }
        return family != 1_040 && family != 1_050 && family != 1_060;
    }

    private static void require(boolean added, String description) {
        if (!added) {
            throw new IllegalStateException("could not provision " + description);
        }
    }

    private static long mix(long seed, long... values) {
        long mixed = seed;
        for (long value : values) {
            mixed ^= value + 0x9E3779B97F4A7C15L + (mixed << 6) + (mixed >>> 2);
        }
        return mixed;
    }

    private record ObservationCandidate(String career, int level, long loadoutSeed, int sniperPillsNeeded,
                                       double minimumHitChance, boolean meetsAccuracyMinimum, int careerOrder) {
    }

    record Prepared(String name, int level, String career, String bundleId, String apProfileId, String spProfileId,
                    List<Integer> equipmentItemIds, int projectileItemId, double minimumHitChance,
                    boolean completeBuild, int remainingAp, int[] remainingSps,
                    int weaponItemId, int weaponAttack) {
        Prepared {
            equipmentItemIds = List.copyOf(equipmentItemIds);
            remainingSps = remainingSps == null ? new int[0] : remainingSps.clone();
        }
    }
}
