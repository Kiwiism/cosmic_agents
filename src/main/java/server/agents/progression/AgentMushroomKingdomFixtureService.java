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
    private static final int OLD_RAGGEDY_CAPE_ITEM_ID = 1_102_053;
    private static final int POWER_ELIXIR_ITEM_ID = 2_000_005;
    private static final int ALL_CURE_ITEM_ID = 2_050_004;
    private static final int SNIPER_PILL_ITEM_ID = 2_002_008;
    private static final int BOW_ARROW_ITEM_ID = 2_060_000;
    private static final int CROSSBOW_ARROW_ITEM_ID = 2_061_000;
    private static final int THROWING_STAR_ITEM_ID = 2_070_000;
    private static final int BULLET_ITEM_ID = 2_330_000;
    private static final int NEAREST_TOWN_SCROLL_ITEM_ID = 2_030_000;
    private static final int SECOND_JOB_TRAVEL_MESO = 100_000;
    private static final byte LARGE_INVENTORY = 96;
    private static final Map<Integer, Integer> LEGAL_SUB_LEVEL_25_WEAPON_BY_JOB = Map.of(
            320, 1_462_003,
            420, 1_332_013);
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
        return prepare(entry, branch, ordinal, seed, nowMs, true, false, false);
    }

    /**
     * Uses the same level-30 build, equipment, and supply fixture as Mushroom Kingdom while
     * deliberately retaining the first job so the real second-job plan can be exercised.
     */
    public static Prepared prepareForSecondJobAdvancement(AgentRuntimeEntry entry,
                                                           AgentSecondJobCatalog.Branch branch,
                                                           int ordinal, long seed,
                                                           long nowMs) throws IOException {
        return prepare(entry, branch, ordinal, seed, nowMs, false, false, false);
    }

    /** Prepares a named test Agent while retaining its saved skin, face, hair, and gender. */
    public static Prepared prepareExistingCharacterForSecondJobAdvancement(
            AgentRuntimeEntry entry, AgentSecondJobCatalog.Branch branch,
            int ordinal, long seed, long nowMs) throws IOException {
        return prepare(entry, branch, ordinal, seed, nowMs, false, true, true);
    }

    private static Prepared prepare(AgentRuntimeEntry entry,
                                    AgentSecondJobCatalog.Branch branch,
                                    int ordinal, long seed, long nowMs,
                                    boolean advanceSecondJob, boolean preserveAppearance,
                                    boolean provisionTravel) throws IOException {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || branch == null) throw new IllegalArgumentException("live Agent and branch required");
        String career = firstJobCareer(branch);
        VictoriaFirstJobMvpTestService.resetAndStart(entry, career, "lv10",
                VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "Mushroom Kingdom fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);

        if (!preserveAppearance) {
            CosmicMapleIslandCohortIdentity.apply(agent,
                    MapleIslandCohortCharacterCatalog.template(ordinal));
        }
        while (agent.getLevel() < 30) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        if (agent.getLevel() != 30) throw new IllegalStateException("fixture character is above level 30");
        if (advanceSecondJob) {
            AgentStarterKitService.advanceJob(entry, Job.getById(branch.targetJobId()));
        } else if (agent.getJob().getId() != branch.firstJobId()) {
            throw new IllegalStateException("fixture expected first job " + branch.firstJobId()
                    + " but found " + agent.getJob().getId());
        }

        List<String> legalProfiles = legalProfiles(branch);
        String apProfile = legalProfiles.get(new SplittableRandom(seed).nextInt(legalProfiles.size()));
        agent.resetAbilityPointsForCurrentLevel();
        entry.apBuildProfileState().clear();
        AgentApBuildProfileService.select(entry, apProfile);
        if (advanceSecondJob) AgentSpBuildProfileService.select(entry, branch.spProfileId());

        List<Integer> equipment = equip(agent, branch, seed);
        provisionSupplies(agent, branch);
        if (provisionTravel) provisionSecondJobTravel(agent, branch);
        if (advanceSecondJob) resetQuestline(agent, branch);
        else resetSecondJobAdvancement(entry, agent);
        agent.healHpMp();
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), branch.id(), branch.targetJobId(), apProfile,
                branch.spProfileId(), agent.getGender(), equipment);
    }

    private static void resetSecondJobAdvancement(AgentRuntimeEntry entry, Character agent) {
        entry.capabilityStates().remove(AgentSecondJobAdvancementState.STATE_KEY);
        for (int questId = 100000; questId <= 100011; questId++) {
            Quest.getInstance(questId).reset(agent);
        }
        Quest.getInstance(2191).reset(agent);
        Quest.getInstance(2192).reset(agent);
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
        // The level-25 gun requires 25 STR; the low-STR level-30 profile is not equipment-legal.
        if (branch.targetJobId() == 520) return List.of("pirate-str30-dex-lv30-v1");
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
        if (weaponId == 0 && LEGAL_SUB_LEVEL_25_WEAPON_BY_JOB.containsKey(branch.targetJobId())) {
            int fallbackItemId = LEGAL_SUB_LEVEL_25_WEAPON_BY_JOB.get(branch.targetJobId());
            Equip fallback = items.getEquipById(fallbackItemId);
            if (fallback != null && items.meetsEquipRequirements(fallback, agent.getJob(), agent.getLevel(),
                    agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) {
                weaponId = fallbackItemId;
            }
        }
        int shoesId = pick(candidates, "So", random, false);
        if (weaponId == 0 || shoesId == 0) {
            throw new IllegalStateException(branch.id() + " has no legal level 25-30 "
                    + (weaponId == 0 ? "weapon" : "shoes") + " preset at STR=" + agent.getStr()
                    + ", DEX=" + agent.getDex() + ", INT=" + agent.getInt() + ", LUK=" + agent.getLuk()
                    + "; legal weapon candidates=" + candidates.getOrDefault("Wp", List.of())
                    + ", shoes candidates=" + candidates.getOrDefault("So", List.of()));
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
        selected.add(OLD_RAGGEDY_CAPE_ITEM_ID);

        Inventory equipped = new Inventory(agent, InventoryType.EQUIPPED,
                agent.getInventory(InventoryType.EQUIPPED).getSlotLimit());
        for (int itemId : selected) {
            Equip equip = items.getEquipById(itemId);
            if (equip == null) throw new IllegalStateException("missing fixture equipment " + itemId);
            short position = itemId == OLD_RAGGEDY_CAPE_ITEM_ID ? -9
                    : equippedSlot(normalizedSlot(items.getEquipmentSlot(itemId)));
            if (position == 0) throw new IllegalStateException("unsupported fixture slot for " + itemId);
            if (itemId == weaponId) applyFiveWeaponScrolls(equip, itemId);
            if (itemId == shoesId) applyShoesFixture(equip);
            if (position == -1 && needsMeleeAccuracyFixture(branch)) {
                applyMeleeAccuracyFixture(equip);
            }
            if (itemId == OLD_RAGGEDY_CAPE_ITEM_ID) applyCapeFixture(equip, branch);
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

    private static boolean needsMeleeAccuracyFixture(AgentSecondJobCatalog.Branch branch) {
        return branch.family() == AgentSecondJobCatalog.Family.WARRIOR
                || branch.targetJobId() == 510;
    }

    private static void applyMeleeAccuracyFixture(Equip equip) {
        Map<String, Integer> stats = ItemInformationProvider.getInstance()
                .getEquipStats(2_040_016); // Helmet Accuracy 10%: +4 ACC, +2 DEX.
        if (stats == null || stats.isEmpty() || equip.getUpgradeSlots() < 5) {
            throw new IllegalStateException("melee helmet lacks five accuracy-scroll slots");
        }
        for (int i = 0; i < 5; i++) ItemInformationProvider.improveEquipStats(equip, stats);
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
        require(inventory.addItem(agent, POWER_ELIXIR_ITEM_ID, (short) 2_000), "Power Elixirs");
        require(inventory.addItem(agent, ALL_CURE_ITEM_ID, (short) 500), "All Cures");
        require(inventory.addItem(agent, SNIPER_PILL_ITEM_ID, (short) 200), "Sniper Pills");
        int projectile = switch (branch.targetJobId()) {
            case 310 -> BOW_ARROW_ITEM_ID;
            case 320 -> CROSSBOW_ARROW_ITEM_ID;
            case 410 -> THROWING_STAR_ITEM_ID;
            case 520 -> BULLET_ITEM_ID;
            default -> 0;
        };
        if (projectile > 0) require(inventory.addItem(agent, projectile, (short) 30_000), "projectiles");
    }

    private static void provisionSecondJobTravel(
            Character agent, AgentSecondJobCatalog.Branch branch) {
        if (agent.getMeso() < SECOND_JOB_TRAVEL_MESO) {
            agent.gainMeso(SECOND_JOB_TRAVEL_MESO - agent.getMeso(), false, false, false);
        }
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        require(inventory.addItem(agent, NEAREST_TOWN_SCROLL_ITEM_ID, (short) 10),
                "Return Scrolls - Nearest Town");
        int townScrollItemId = secondJobTownScrollItemId(branch);
        if (townScrollItemId != NEAREST_TOWN_SCROLL_ITEM_ID) {
            require(inventory.addItem(agent, townScrollItemId, (short) 5),
                    "second-job town return scrolls");
        }
    }

    static int secondJobTownScrollItemId(AgentSecondJobCatalog.Branch branch) {
        return switch (branch.family()) {
            case WARRIOR -> 2_030_003; // Perion
            case MAGICIAN -> 2_030_002; // Ellinia
            case BOWMAN -> 2_030_001; // Henesys
            case THIEF -> 2_030_005; // Kerning City
            case PIRATE -> NEAREST_TOWN_SCROLL_ITEM_ID;
        };
    }

    private static void resetQuestline(Character agent, AgentSecondJobCatalog.Branch branch) {
        for (int questId = 2300; questId <= 2304; questId++) Quest.getInstance(questId).reset(agent);
        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            Quest.getInstance(node.questId()).reset(agent);
        }
        for (int questId : List.of(2337, 2338, 2342)) Quest.getInstance(questId).reset(agent);
        Quest.getInstance(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                .reset(agent);
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
