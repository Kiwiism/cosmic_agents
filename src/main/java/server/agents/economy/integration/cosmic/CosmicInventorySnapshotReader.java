package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.economy.ownership.InventoryItemRef;
import server.agents.economy.ownership.InventoryItemSnapshot;
import server.agents.economy.ownership.InventorySnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Side-effect-free adapter from Cosmic's live inventory to the economy ownership contract. */
public final class CosmicInventorySnapshotReader {
    private static final List<InventoryType> TYPES = List.of(InventoryType.EQUIPPED,
            InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC,
            InventoryType.CASH);

    public InventorySnapshot read(Character character) {
        List<InventoryItemSnapshot> items = new ArrayList<>();
        for (InventoryType type : TYPES) {
            for (Item item : character.getInventory(type).list()) {
                if (item.getQuantity() <= 0) continue;
                Map<String, Object> attributes = attributes(item);
                String fingerprint = digest(identity(type, item, attributes));
                items.add(new InventoryItemSnapshot(new InventoryItemRef(type.name(), item.getPosition(),
                        item.getItemId(), fingerprint), item.getQuantity(), attributes));
            }
        }
        items.sort(Comparator.comparing((InventoryItemSnapshot item) -> item.ref().inventoryType())
                .thenComparingInt(item -> item.ref().slot()).thenComparingInt(item -> item.ref().itemId()));
        StringBuilder revision = new StringBuilder();
        items.forEach(item -> revision.append(item.ref().inventoryType()).append('|')
                .append(item.ref().slot()).append('|').append(item.ref().itemId()).append('|')
                .append(item.ref().fingerprint()).append('|').append(item.quantity()).append('\n'));
        return new InventorySnapshot(character.getId(), digest(revision.toString()), items);
    }

    private static Map<String, Object> attributes(Item item) {
        Map<String, Object> value = new TreeMap<>();
        value.put("flag", item.getFlag()); value.put("owner", item.getOwner());
        value.put("expiration", item.getExpiration()); value.put("serialNumber", item.getSN());
        value.put("petId", item.getPetId()); value.put("giftFrom", item.getGiftFrom());
        if (item instanceof Equip equip) {
            value.put("upgradeSlots", equip.getUpgradeSlots()); value.put("level", equip.getLevel());
            value.put("str", equip.getStr()); value.put("dex", equip.getDex());
            value.put("int", equip.getInt()); value.put("luk", equip.getLuk());
            value.put("hp", equip.getHp()); value.put("mp", equip.getMp());
            value.put("weaponAttack", equip.getWatk()); value.put("magicAttack", equip.getMatk());
            value.put("weaponDefense", equip.getWdef()); value.put("magicDefense", equip.getMdef());
            value.put("accuracy", equip.getAcc()); value.put("avoidability", equip.getAvoid());
            value.put("hands", equip.getHands()); value.put("speed", equip.getSpeed());
            value.put("jump", equip.getJump()); value.put("viciousHammer", equip.getVicious());
        }
        return Map.copyOf(value);
    }

    private static String identity(InventoryType type, Item item, Map<String, Object> attributes) {
        return type.name() + '|' + item.getPosition() + '|' + item.getItemId() + '|' + attributes;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
