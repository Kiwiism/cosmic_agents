package server.agents.field;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.VictoriaFirstJobMvpTestService;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Deterministically resets a dedicated pooled Agent for a long observation run. */
final class AgentFieldObservationFixtureService {
    private static final int POWER_ELIXIR_ITEM_ID = 2_000_005;
    private static final int ALL_CURE_ITEM_ID = 2_050_004;
    private static final int BOW_ARROW_ITEM_ID = 2_060_000;
    private static final int CROSSBOW_ARROW_ITEM_ID = 2_061_000;
    private static final int THROWING_STAR_ITEM_ID = 2_070_000;
    private static final int BULLET_ITEM_ID = 2_330_000;
    private static final short POWER_ELIXIRS = 2_000;
    private static final short ALL_CURES = 500;
    private static final short PROJECTILES = 30_000;
    private static final byte OBSERVATION_USE_SLOTS = 96;
    private static final List<String> CAREERS = List.of(
            "warrior", "bowman", "magician", "thief-claw", "thief-dagger",
            "pirate-gun", "pirate-knuckle");

    private AgentFieldObservationFixtureService() {
    }

    static Prepared prepare(AgentRuntimeEntry entry, int level, long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || level < 15 || level > 25) {
            throw new IllegalArgumentException("a spawned Agent and level 15-25 are required");
        }
        String career = CAREERS.get((int) Math.floorMod(seed, CAREERS.size()));
        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                entry, career, "lv10", VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "field observation fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);
        while (agent.getLevel() < level) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        if (agent.getLevel() != level) {
            throw new IllegalStateException("pooled fixture is already above requested level " + level);
        }
        AgentApBuildProfileService.autoAssign(entry, agent);
        AgentSpBuildProfileService.autoAssign(entry, agent);
        List<Integer> equipment = applyDeterministicEquipmentLoadout(agent, seed);
        provisionTwoHourSupplies(agent);
        agent.healHpMp();
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), level, bundle.bundleId(), bundle.apProfileId(),
                bundle.spProfileId(), equipment, suppliedProjectile(agent));
    }

    private static List<Integer> applyDeterministicEquipmentLoadout(Character agent, long seed) {
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        clearInventory(agent, InventoryType.EQUIP, agent.getInventory(InventoryType.EQUIP).getSlotLimit());
        clearInventory(agent, InventoryType.EQUIPPED, agent.getInventory(InventoryType.EQUIPPED).getSlotLimit());
        Map<String, List<Integer>> candidatesBySlot = new LinkedHashMap<>();
        for (int itemId : AgentFieldObservationEquipmentRepository.itemIds()) {
            Equip equip = inventory.getEquipById(itemId);
            if (equip == null || inventory.getEquipLevelRequirement(itemId) > agent.getLevel()
                    || !genderCompatible(agent.getGender(), itemId)
                    || !inventory.meetsEquipRequirements(equip, agent.getJob(), agent.getLevel(),
                    agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) {
                continue;
            }
            WeaponType weaponType = inventory.getWeaponType(itemId);
            if (weaponType != null && weaponType != WeaponType.NOT_A_WEAPON
                    && !weaponCompatible(agent, weaponType)) {
                continue;
            }
            String slot = normalizedSlot(inventory.getEquipmentSlot(itemId));
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
            selectOne(agent, inventory, candidatesBySlot, slot, random, selected);
        }
        int weaponItemId = selectOne(agent, inventory, candidatesBySlot, "Wp", random, selected);
        if (weaponItemId == 0) {
            throw new IllegalStateException("deterministic observation preset has no compatible weapon");
        }
        WeaponType weaponType = inventory.getWeaponType(weaponItemId);
        if (supportsShield(weaponType, inventory.getEquipmentSlot(weaponItemId))) {
            selectOne(agent, inventory, candidatesBySlot, "Si", random, selected);
        }
        AgentEquipmentService.autoEquip(agent, null, null, true);
        Item weapon = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            throw new IllegalStateException("deterministic observation preset has no compatible weapon");
        }
        return List.copyOf(selected);
    }

    private static int selectOne(Character agent,
                                 InventoryGateway inventory,
                                 Map<String, List<Integer>> candidatesBySlot,
                                 String slot,
                                 SplittableRandom random,
                                 List<Integer> selected) {
        List<Integer> candidates = candidatesBySlot.getOrDefault(slot, List.of()).stream()
                .sorted(Comparator.naturalOrder()).toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        int itemId = candidates.get(random.nextInt(candidates.size()));
        if (!inventory.addItem(agent, itemId, (short) 1)) {
            throw new IllegalStateException("could not add observation equipment " + itemId);
        }
        selected.add(itemId);
        return itemId;
    }

    private static void provisionTwoHourSupplies(Character agent) {
        clearInventory(agent, InventoryType.USE,
                (byte) Math.max(Byte.toUnsignedInt(agent.getInventory(InventoryType.USE).getSlotLimit()),
                        Byte.toUnsignedInt(OBSERVATION_USE_SLOTS)));
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        require(inventory.addItem(agent, POWER_ELIXIR_ITEM_ID, POWER_ELIXIRS), "Power Elixirs");
        require(inventory.addItem(agent, ALL_CURE_ITEM_ID, ALL_CURES), "All Cure Potions");
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

    private static boolean weaponCompatible(Character agent, WeaponType weaponType) {
        if (agent.getJob() != Job.WARRIOR) {
            return AgentEquipmentService.isWeaponCompatible(agent, weaponType);
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

    record Prepared(String name, int level, String bundleId, String apProfileId, String spProfileId,
                    List<Integer> equipmentItemIds, int projectileItemId) {
        Prepared {
            equipmentItemIds = List.copyOf(equipmentItemIds);
        }
    }
}
