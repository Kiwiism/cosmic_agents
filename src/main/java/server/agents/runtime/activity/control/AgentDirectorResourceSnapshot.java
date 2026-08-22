package server.agents.runtime.activity.control;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.supplies.AgentPotionService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only resources needed to make and explain Director decisions. */
public record AgentDirectorResourceSnapshot(
        int exp,
        int remainingAp,
        int remainingSp,
        int hpPotions,
        int mpPotions,
        String weaponType,
        int ammunition,
        boolean ammunitionRequired,
        boolean ammunitionUnlimited,
        Map<String, Integer> freeInventorySlots,
        Map<String, Integer> equippedItemIds) {

    public AgentDirectorResourceSnapshot {
        weaponType = weaponType == null ? "" : weaponType.trim();
        freeInventorySlots = Map.copyOf(
                freeInventorySlots == null ? Map.of() : freeInventorySlots);
        equippedItemIds = Map.copyOf(equippedItemIds == null ? Map.of() : equippedItemIds);
        if (exp < 0 || remainingAp < 0 || remainingSp < 0
                || hpPotions < 0 || mpPotions < 0 || ammunition < 0) {
            throw new IllegalArgumentException("non-negative Agent resources are required");
        }
    }

    public static AgentDirectorResourceSnapshot capture(Character agent) {
        if (agent == null) throw new IllegalArgumentException("live Agent is required");
        int[] potions = AgentPotionService.countPotions(agent);
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        boolean requiresAmmo = AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType);
        int ammunition = requiresAmmo
                ? AgentCombatAmmoCounter.countAmmo(agent, weaponType) : 0;
        boolean unlimitedAmmo = ammunition == Integer.MAX_VALUE;
        if (unlimitedAmmo) ammunition = 0;
        Map<String, Integer> freeSlots = new LinkedHashMap<>();
        for (InventoryType type : new InventoryType[]{
                InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP,
                InventoryType.ETC, InventoryType.CASH}) {
            Inventory inventory = agent.getInventory(type);
            freeSlots.put(type.name(), inventory == null ? 0 : (int) inventory.getNumFreeSlot());
        }
        Map<String, Integer> equipped = new LinkedHashMap<>();
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);
        if (equippedInventory != null) {
            for (Item item : equippedInventory.list()) {
                equipped.put(Short.toString(item.getPosition()), item.getItemId());
            }
        }
        return new AgentDirectorResourceSnapshot(
                Math.max(0, agent.getExp()), Math.max(0, agent.getRemainingAp()),
                Math.max(0, agent.getRemainingSp()), Math.max(0, potions[0]),
                Math.max(0, potions[1]), weaponType == null ? "" : weaponType.name(),
                Math.max(0, ammunition), requiresAmmo, unlimitedAmmo, freeSlots, equipped);
    }
}
