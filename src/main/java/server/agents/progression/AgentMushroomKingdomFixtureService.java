package server.agents.progression;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import server.ItemInformationProvider;
import server.agents.capabilities.build.AgentStarterKitService;
import server.agents.capabilities.build.profiles.AgentApBuildProfile;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.field.AgentFieldObservationEquipmentRepository;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.runtime.AgentRuntimeEntry;
import server.quest.Quest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Deterministic, legal level-30 fixture for one Mushroom Kingdom cohort member. */
public final class AgentMushroomKingdomFixtureService {
    private static final int OLD_RAGGEDY_CAPE = 1_102_053;
    private static final int POWER_ELIXIR = 2_000_005;
    private static final int ALL_CURE = 2_050_004;
    private static final int SNIPER_PILL = 2_002_008;
    private static final int BOW_ARROW = 2_060_000;
    private static final int CROSSBOW_ARROW = 2_061_000;
    private static final int THROWING_STAR = 2_070_000;
    private static final int BULLET = 2_330_000;
    private static final byte LARGE_INVENTORY = 96;
    private static final Map<AgentApBuildProfile.JobFamily, List<String>> EQUIPMENT_LEGAL_AP = Map.of(
            AgentApBuildProfile.JobFamily.WARRIOR,
            List.of("warrior-dex20-str-lv30-v1", "warrior-dex40-str-lv30-v1"),
            AgentApBuildProfile.JobFamily.MAGICIAN,
            List.of("magician-luk33-int-lv30-v1"),
            AgentApBuildProfile.JobFamily.BOWMAN,
            List.of("bowman-str30-dex-lv30-v1"),
            AgentApBuildProfile.JobFamily.THIEF,
            List.of("thief-dex60-luk-lv30-v1"),
            AgentApBuildProfile.JobFamily.PIRATE,
            List.of("pirate-str30-dex-lv30-v1", "pirate-str20-dex-lv30-v1",
                    "pirate-dex30-str-lv30-v1"));

    private AgentMushroomKingdomFixtureService() { }

    public static Prepared prepare(AgentRuntimeEntry entry,
                                   AgentSecondJobCatalog.Branch branch,
                                   int ordinal, long seed, long nowMs) throws IOException {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || branch == null) throw new IllegalArgumentException("live Agent and branch required");
        String career = firstJobCareer(branch);
        VictoriaFirstJobMvpTestService.resetAndStart(entry, career, "lv10",
                VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "Mushroom Kingdom fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);

        CosmicMapleIslandCohortIdentity.apply(agent,
                MapleIslandCohortCharacterCatalog.template(ordinal));
        while (agent.getLevel() < 30) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        if (agent.getLevel() != 30) throw new IllegalStateException("fixture character is above level 30");
        AgentStarterKitService.advanceJob(entry, Job.getById(branch.targetJobId()));

        List<String> legalProfiles = legalProfiles(branch);
        String apProfile = legalProfiles.get(new SplittableRandom(seed).nextInt(legalProfiles.size()));
        agent.resetAbilityPointsForCurrentLevel();
        entry.apBuildProfileState().clear();
        AgentApBuildProfileService.select(entry, apProfile);
        AgentSpBuildProfileService.select(entry, branch.spProfileId());

        List<Integer> equipment = equip(agent, branch, seed);
        provisionSupplies(agent, branch);
        resetQuestline(agent, branch);
        agent.healHpMp();
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), branch.id(), branch.targetJobId(), apProfile,
                branch.spProfileId(), agent.getGender(), equipment);
    }

    private static List<String> legalProfiles(AgentSecondJobCatalog.Branch branch) {
        AgentApBuildProfile.JobFamily family = switch (branch.family()) {
            case WARRIOR -> AgentApBuildProfile.JobFamily.WARRIOR;
            case MAGICIAN -> AgentApBuildProfile.JobFamily.MAGICIAN;
            case BOWMAN -> AgentApBuildProfile.JobFamily.BOWMAN;
            case THIEF -> AgentApBuildProfile.JobFamily.THIEF;
            case PIRATE -> AgentApBuildProfile.JobFamily.PIRATE;
        };
        List<String> candidates = EQUIPMENT_LEGAL_AP.get(family);
        if (branch.targetJobId() == 510) return List.of("pirate-dex30-str-lv30-v1");
        if (branch.targetJobId() == 520) return candidates.stream()
                .filter(id -> !id.contains("dex30-str")).toList();
        return candidates;
    }

    private static List<Integer> equip(Character agent, AgentSecondJobCatalog.Branch branch, long seed) {
        InventoryGateway items = AgentInventoryGatewayRuntime.inventory();
        SplittableRandom random = new SplittableRandom(seed);
        Map<String, List<Integer>> candidates = new LinkedHashMap<>();
        for (int itemId : AgentFieldObservationEquipmentRepository.itemIds()) {
            Equip equip = items.getEquipById(itemId);
            int level = items.getEquipLevelRequirement(itemId);
            if (equip == null || level < 25 || level > 30 || !genderCompatible(agent.getGender(), itemId)
                    || !items.meetsEquipRequirements(equip, agent.getJob(), agent.getLevel(),
                    agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) continue;
            String slot = normalizedSlot(items.getEquipmentSlot(itemId));
            if ("Wp".equals(slot) && !branchWeapon(branch, itemId)) continue;
            if (!slot.isBlank()) candidates.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(itemId);
        }
        int weaponId = pick(candidates, "Wp", random, true);
        int shoesId = pick(candidates, "So", random, false);
        if (weaponId == 0 || shoesId == 0) {
            throw new IllegalStateException(branch.id() + " has no legal level 25-30 weapon/shoes preset");
        }
        ArrayList<Integer> selected = new ArrayList<>();
        selected.add(weaponId);
        selected.add(shoesId);
        addIfPresent(selected, pick(candidates, "Cp", random, false));
        boolean overall = candidates.containsKey("MaPn") && random.nextBoolean();
        if (overall) addIfPresent(selected, pick(candidates, "MaPn", random, false));
        else {
            addIfPresent(selected, pick(candidates, "Ma", random, false));
            addIfPresent(selected, pick(candidates, "Pn", random, false));
        }
        addIfPresent(selected, pick(candidates, "Gv", random, false));
        addIfPresent(selected, pick(candidates, "Ae", random, false));
        if (supportsShield(weaponId) && candidates.containsKey("Si")) {
            addIfPresent(selected, pick(candidates, "Si", random, false));
        }
        selected.add(OLD_RAGGEDY_CAPE);

        Inventory equipped = new Inventory(agent, InventoryType.EQUIPPED,
                agent.getInventory(InventoryType.EQUIPPED).getSlotLimit());
        for (int itemId : selected) {
            Equip equip = items.getEquipById(itemId);
            if (equip == null) throw new IllegalStateException("missing fixture equipment " + itemId);
            short position = itemId == OLD_RAGGEDY_CAPE ? -9 : equippedSlot(normalizedSlot(items.getEquipmentSlot(itemId)));
            if (position == 0) throw new IllegalStateException("unsupported fixture slot for " + itemId);
            if (itemId == weaponId) applyFiveWeaponScrolls(equip, itemId);
            if (itemId == shoesId) applyShoesFixture(equip);
            if (itemId == OLD_RAGGEDY_CAPE) applyCapeFixture(equip, branch);
            equip.setPosition(position);
            equipped.addItemFromDB(equip);
        }
        agent.setInventory(InventoryType.EQUIP,
                new Inventory(agent, InventoryType.EQUIP, LARGE_INVENTORY));
        agent.setInventory(InventoryType.EQUIPPED, equipped);
        agent.recalcLocalStats();
        return List.copyOf(selected);
    }

    private static void applyFiveWeaponScrolls(Equip equip, int weaponId) {
        int scrollId = 2_043_001 + (weaponId / 10_000 - 130) * 100;
        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(scrollId);
        if (stats == null || stats.isEmpty() || equip.getUpgradeSlots() < 5) {
            throw new IllegalStateException("missing five-slot 60% scroll fixture for weapon " + weaponId);
        }
        for (int i = 0; i < 5; i++) ItemInformationProvider.improveEquipStats(equip, stats);
        equip.setUpgradeSlots((byte) (equip.getUpgradeSlots() - 5));
        equip.setLevel((byte) (equip.getLevel() + 5));
    }

    private static void applyShoesFixture(Equip equip) {
        if (equip.getUpgradeSlots() < 5) throw new IllegalStateException("shoes lack five upgrade slots");
        equip.setSpeed((short) (equip.getSpeed() + 10));
        equip.setUpgradeSlots((byte) (equip.getUpgradeSlots() - 5));
        equip.setLevel((byte) (equip.getLevel() + 5));
    }

    private static void applyCapeFixture(Equip equip, AgentSecondJobCatalog.Branch branch) {
        if (equip.getUpgradeSlots() < 5) throw new IllegalStateException("cape lacks five upgrade slots");
        switch (branch.family()) {
            case WARRIOR -> equip.setStr((short) (equip.getStr() + 10));
            case MAGICIAN -> equip.setInt((short) (equip.getInt() + 10));
            case BOWMAN, PIRATE -> equip.setDex((short) (equip.getDex() + 10));
            case THIEF -> equip.setLuk((short) (equip.getLuk() + 10));
        }
        equip.setUpgradeSlots((byte) (equip.getUpgradeSlots() - 5));
        equip.setLevel((byte) (equip.getLevel() + 5));
    }

    private static void provisionSupplies(Character agent, AgentSecondJobCatalog.Branch branch) {
        agent.setInventory(InventoryType.USE, new Inventory(agent, InventoryType.USE, LARGE_INVENTORY));
        agent.setInventory(InventoryType.ETC, new Inventory(agent, InventoryType.ETC, LARGE_INVENTORY));
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        require(inventory.addItem(agent, POWER_ELIXIR, (short) 2_000), "Power Elixirs");
        require(inventory.addItem(agent, ALL_CURE, (short) 500), "All Cures");
        require(inventory.addItem(agent, SNIPER_PILL, (short) 200), "Sniper Pills");
        int projectile = switch (branch.targetJobId()) {
            case 310 -> BOW_ARROW;
            case 320 -> CROSSBOW_ARROW;
            case 410 -> THROWING_STAR;
            case 520 -> BULLET;
            default -> 0;
        };
        if (projectile > 0) require(inventory.addItem(agent, projectile, (short) 30_000), "projectiles");
    }

    private static void resetQuestline(Character agent, AgentSecondJobCatalog.Branch branch) {
        for (int questId = 2300; questId <= 2304; questId++) Quest.getInstance(questId).reset(agent);
        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            Quest.getInstance(node.questId()).reset(agent);
        }
        for (int questId : List.of(2337, 2338, 2342)) Quest.getInstance(questId).reset(agent);
        Quest.getInstance(100202).reset(agent);
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(branch.targetJobId());
        Quest.getInstance(entryQuest).forceStart(agent, AgentMushroomKingdomCatalog.entryLeaderNpc(entryQuest));
        require(AgentInventoryGatewayRuntime.inventory().addItem(agent, 4_032_375, (short) 1),
                "Explorer recommendation letter");
    }

    private static boolean branchWeapon(AgentSecondJobCatalog.Branch branch, int itemId) {
        int family = itemId / 10_000;
        return switch (branch.targetJobId()) {
            case 110 -> family == 130 || family == 140 || family == 131 || family == 141;
            case 120 -> family == 130 || family == 140 || family == 132 || family == 142;
            case 130 -> family == 143 || family == 144;
            case 210, 220, 230 -> family == 137 || family == 138;
            case 310 -> family == 145;
            case 320 -> family == 146;
            case 410 -> family == 147;
            case 420 -> family == 133;
            case 510 -> family == 148;
            case 520 -> family == 149;
            default -> false;
        };
    }

    private static boolean supportsShield(int weaponId) {
        return Set.of(130, 131, 132, 133, 137, 138).contains(weaponId / 10_000);
    }

    private static int pick(Map<String, List<Integer>> bySlot, String slot,
                            SplittableRandom random, boolean strongest) {
        List<Integer> values = bySlot.getOrDefault(slot, List.of());
        if (values.isEmpty()) return 0;
        if (strongest) return values.stream().max(Comparator.comparingInt(itemId -> {
            Equip equip = AgentInventoryGatewayRuntime.inventory().getEquipById(itemId);
            return equip == null ? 0 : Math.max(equip.getWatk(), equip.getMatk());
        })).orElse(0);
        return values.get(random.nextInt(values.size()));
    }

    private static String normalizedSlot(String slot) {
        if (slot == null) return "";
        if (slot.equals("Wp") || slot.equals("WpSi")) return "Wp";
        return switch (slot) {
            case "Cp", "Ae", "Ma", "MaPn", "Pn", "So", "Gv", "Si" -> slot;
            default -> "";
        };
    }

    private static short equippedSlot(String slot) {
        return switch (slot) {
            case "Cp" -> -1; case "Ae" -> -4; case "Ma", "MaPn" -> -5;
            case "Pn" -> -6; case "So" -> -7; case "Gv" -> -8;
            case "Si" -> -10; case "Wp" -> -11; default -> 0;
        };
    }

    private static boolean genderCompatible(int gender, int itemId) {
        int family = itemId / 1_000;
        return gender == 0 ? family != 1_041 && family != 1_051 && family != 1_061
                : family != 1_040 && family != 1_050 && family != 1_060;
    }

    private static String firstJobCareer(AgentSecondJobCatalog.Branch branch) {
        return switch (branch.targetJobId()) {
            case 110, 120, 130 -> "warrior";
            case 210, 220, 230 -> "magician";
            case 310, 320 -> "bowman";
            case 410 -> "thief-claw";
            case 420 -> "thief-dagger";
            case 510 -> "pirate-knuckle";
            case 520 -> "pirate-gun";
            default -> throw new IllegalArgumentException("unsupported branch " + branch.id());
        };
    }

    private static void addIfPresent(List<Integer> selected, int itemId) {
        if (itemId > 0) selected.add(itemId);
    }

    private static void require(boolean success, String description) {
        if (!success) throw new IllegalStateException("could not provision " + description);
    }

    public record Prepared(String name, String branchId, int jobId, String apProfileId,
                           String spProfileId, int gender, List<Integer> equipmentItemIds) {
        public Prepared { equipmentItemIds = List.copyOf(equipmentItemIds); }
    }
}
