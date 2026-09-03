package server.agents.progression;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.ScrollTransactionService;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.integration.AgentClientGatewayRuntime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact King Pepe weapon, class-box, and quest-2337 reward mapping. */
public final class AgentPepeEquipmentCatalog {
    private record Weapon(int itemId, int scrollItemId, int rewardSelectionIndex) { }

    private static final Map<Integer, Weapon> BY_CATEGORY = weapons();

    private AgentPepeEquipmentCatalog() { }

    public static AgentPepeEquipmentSnapshot capture(Character agent) {
        if (agent == null || agent.getJob() == null) return AgentPepeEquipmentSnapshot.NONE;
        int category = desiredCategory(agent);
        Weapon desired = BY_CATEGORY.get(category);
        if (desired == null) return AgentPepeEquipmentSnapshot.NONE;
        Equip owned = find(agent, desired.itemId());
        Item equipped = inventory(agent, InventoryType.EQUIPPED) == null ? null
                : inventory(agent, InventoryType.EQUIPPED).getItem((short) -11);
        return new AgentPepeEquipmentSnapshot(
                desired.itemId(), weaponType(category).name(), owned != null,
                equipped != null && equipped.getItemId() == desired.itemId(),
                owned == null ? 0 : Math.max(0, owned.getUpgradeSlots()),
                desired.scrollItemId(), desired.rewardSelectionIndex());
    }

    public static int weaponBoxItemId(int jobId) {
        int family = explorerFamily(jobId);
        return family == 0 ? 0 : 2_022_569 + family;
    }

    public static int mixedBoxItemId(int jobId) {
        int family = explorerFamily(jobId);
        return family == 0 ? 0 : 2_022_579 + family;
    }

    public static boolean openRelevantBoxes(Character agent,
                                            server.agents.integration.PrimitiveCapabilityGateway gateway) {
        if (agent == null || gateway == null || agent.getJob() == null) return false;
        boolean opened = false;
        int weaponBox = weaponBoxItemId(agent.getJob().getId());
        int mixedBox = mixedBoxItemId(agent.getJob().getId());
        int weaponBoxes = weaponBox > 0 ? Math.max(0, gateway.itemCount(agent, weaponBox)) : 0;
        for (int i = 0; i < weaponBoxes && gateway.itemCount(agent, weaponBox) > 0; i++) {
            if (!gateway.useItem(agent, weaponBox)) break;
            opened = true;
        }
        int mixedBoxes = mixedBox > 0 ? Math.max(0, gateway.itemCount(agent, mixedBox)) : 0;
        for (int i = 0; i < mixedBoxes && gateway.itemCount(agent, mixedBox) > 0; i++) {
            if (!gateway.useItem(agent, mixedBox)) break;
            opened = true;
        }
        return opened;
    }

    public static ScrollTransactionService.Result applyOwnedScroll(Character agent) {
        AgentPepeEquipmentSnapshot facts = capture(agent);
        if (!facts.scrollable() || !AgentClientGatewayRuntime.clients().hasClient(agent)) return null;
        Item scroll = inventory(agent, InventoryType.USE) == null ? null
                : inventory(agent, InventoryType.USE).findById(facts.scrollItemId());
        if (scroll == null || !AgentEquipmentService.equipPreferredWeapon(
                agent, facts.desiredWeaponItemId())) return null;
        return AgentClientGatewayRuntime.clients().applyScroll(
                agent, scroll.getPosition(), (short) -11, (byte) 0);
    }

    private static Equip find(Character agent, int itemId) {
        for (InventoryType type : new InventoryType[]{InventoryType.EQUIPPED, InventoryType.EQUIP}) {
            Inventory inventory = inventory(agent, type);
            if (inventory == null) continue;
            for (Item item : inventory.list()) {
                if (item instanceof Equip equip && equip.getItemId() == itemId) return equip;
            }
        }
        return null;
    }

    private static Inventory inventory(Character agent, InventoryType type) {
        try {
            return agent.getInventory(type);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static int desiredCategory(Character agent) {
        Inventory equipped = inventory(agent, InventoryType.EQUIPPED);
        Item weapon = equipped == null ? null : equipped.getItem((short) -11);
        int category = weapon == null ? 0 : weapon.getItemId() / 10_000;
        if (BY_CATEGORY.containsKey(category)) return category;
        return switch (agent.getJob().getId()) {
            case 110 -> 140;
            case 120 -> 130;
            case 130 -> 143;
            case 210, 220, 230 -> 137;
            case 310 -> 145;
            case 320 -> 146;
            case 410 -> 147;
            case 420 -> 133;
            case 510 -> 148;
            case 520 -> 149;
            default -> 0;
        };
    }

    private static int explorerFamily(int jobId) {
        int family = jobId / 100;
        return family >= 1 && family <= 5 ? family : 0;
    }

    private static WeaponType weaponType(int category) {
        return switch (category) {
            case 130 -> WeaponType.SWORD1H;
            case 131, 132 -> WeaponType.GENERAL1H_SWING;
            case 133 -> WeaponType.DAGGER_THIEVES;
            case 137 -> WeaponType.WAND;
            case 138 -> WeaponType.STAFF;
            case 140 -> WeaponType.SWORD2H;
            case 141, 142 -> WeaponType.GENERAL2H_SWING;
            case 143 -> WeaponType.SPEAR_STAB;
            case 144 -> WeaponType.POLE_ARM_SWING;
            case 145 -> WeaponType.BOW;
            case 146 -> WeaponType.CROSSBOW;
            case 147 -> WeaponType.CLAW;
            case 148 -> WeaponType.KNUCKLE;
            case 149 -> WeaponType.GUN;
            default -> WeaponType.NOT_A_WEAPON;
        };
    }

    private static Map<Integer, Weapon> weapons() {
        Map<Integer, Weapon> values = new LinkedHashMap<>();
        add(values, 130, 1302119, 2043021, 0);
        add(values, 131, 1312045, 2043116, 1);
        add(values, 132, 1322073, 2043216, 2);
        add(values, 133, 1332088, 2043311, 0);
        add(values, 140, 1402064, 2044024, 3);
        add(values, 141, 1412042, 2044116, 4);
        add(values, 142, 1422045, 2044216, 5);
        add(values, 143, 1432057, 2044316, 6);
        add(values, 144, 1442082, 2044416, 7);
        add(values, 137, 1372053, 2043711, 0);
        add(values, 138, 1382070, 2043811, 1);
        add(values, 145, 1452073, 2044511, 0);
        add(values, 146, 1462066, 2044611, 1);
        add(values, 147, 1472089, 2044711, 1);
        add(values, 148, 1482037, 2044816, 0);
        add(values, 149, 1492038, 2044909, 1);
        return Map.copyOf(values);
    }

    private static void add(Map<Integer, Weapon> values, int category, int weapon,
                            int scroll, int selection) {
        values.put(category, new Weapon(weapon, scroll, selection));
    }
}
