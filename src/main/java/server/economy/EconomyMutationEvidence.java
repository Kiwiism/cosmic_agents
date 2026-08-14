package server.economy;

import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Exact participant deltas captured at the same boundary as durable settlement. */
public record EconomyMutationEvidence(List<ParticipantDelta> participants) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public EconomyMutationEvidence { participants = List.copyOf(participants); }

    static EconomyMutationEvidence between(EconomyParticipantSnapshot primaryBefore,
                                           EconomyParticipantSnapshot primaryAfter,
                                           EconomyParticipantSnapshot secondaryBefore,
                                           EconomyParticipantSnapshot secondaryAfter) {
        List<ParticipantDelta> result = new ArrayList<>();
        result.add(delta(primaryBefore, primaryAfter));
        if (secondaryBefore != null) result.add(delta(secondaryBefore, secondaryAfter));
        return new EconomyMutationEvidence(result);
    }

    public String json() {
        try { return JSON.writeValueAsString(this); }
        catch (JsonProcessingException failure) { throw new EconomyTransactionException("Could not encode economy evidence", failure); }
    }

    private static ParticipantDelta delta(EconomyParticipantSnapshot before,
                                          EconomyParticipantSnapshot after) {
        Map<String, Holding> left = holdings(before);
        Map<String, Holding> right = holdings(after);
        Set<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        List<ItemDelta> changes = new ArrayList<>();
        for (String key : keys) {
            Holding oldValue = left.get(key);
            Holding newValue = right.get(key);
            int beforeQuantity = oldValue == null ? 0 : oldValue.quantity;
            int afterQuantity = newValue == null ? 0 : newValue.quantity;
            if (beforeQuantity != afterQuantity) {
                Holding fact = newValue == null ? oldValue : newValue;
                changes.add(new ItemDelta(fact.itemId, fact.inventoryType, fact.fingerprint,
                        beforeQuantity, afterQuantity, Math.subtractExact(afterQuantity, beforeQuantity),
                        fact.attributes));
            }
        }
        return new ParticipantDelta(before.characterId(), before.mesos(), after.mesos(),
                Math.subtractExact(after.mesos(), before.mesos()), changes);
    }

    private static Map<String, Holding> holdings(EconomyParticipantSnapshot snapshot) {
        Map<String, Holding> result = new HashMap<>();
        snapshot.inventories().forEach((type, inventory) -> {
            for (Item item : inventory.list()) {
                Map<String, Object> attributes = attributes(item);
                String fingerprint = fingerprint(item, attributes);
                String key = type.name() + ':' + item.getItemId() + ':' + fingerprint;
                result.merge(key, new Holding(item.getItemId(), type.name(), fingerprint,
                                item.getQuantity(), attributes),
                        (one, two) -> new Holding(one.itemId, one.inventoryType, one.fingerprint,
                                Math.addExact(one.quantity, two.quantity), one.attributes));
            }
        });
        return result;
    }

    private static Map<String, Object> attributes(Item item) {
        Map<String, Object> value = new TreeMap<>();
        value.put("owner", item.getOwner()); value.put("flag", item.getFlag());
        value.put("expiration", item.getExpiration()); value.put("giftFrom", item.getGiftFrom());
        value.put("petId", item.getPetId()); value.put("sn", item.getSN());
        if (item instanceof Equip equip) {
            value.put("upgradeSlots", equip.getUpgradeSlots()); value.put("level", equip.getLevel());
            value.put("str", equip.getStr()); value.put("dex", equip.getDex()); value.put("int", equip.getInt());
            value.put("luk", equip.getLuk()); value.put("hp", equip.getHp()); value.put("mp", equip.getMp());
            value.put("watk", equip.getWatk()); value.put("matk", equip.getMatk());
            value.put("wdef", equip.getWdef()); value.put("mdef", equip.getMdef());
            value.put("acc", equip.getAcc()); value.put("avoid", equip.getAvoid());
            value.put("hands", equip.getHands()); value.put("speed", equip.getSpeed());
            value.put("jump", equip.getJump()); value.put("vicious", equip.getVicious());
            value.put("itemLevel", equip.getItemLevel()); value.put("itemExp", equip.getItemExp());
            value.put("ringId", equip.getRingId());
        }
        return Map.copyOf(value);
    }

    private static String fingerprint(Item item, Map<String, Object> attributes) {
        try {
            String canonical = item.getItemId() + "|" + JSON.writeValueAsString(attributes);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record ParticipantDelta(int characterId, int mesoBefore, int mesoAfter,
                                   int mesoDelta, List<ItemDelta> itemDeltas) {
        public ParticipantDelta { itemDeltas = List.copyOf(itemDeltas); }
    }
    public record ItemDelta(int itemId, String inventoryType, String fingerprint,
                            int quantityBefore, int quantityAfter, int quantityDelta,
                            Map<String, Object> attributes) {
        public ItemDelta { attributes = Map.copyOf(attributes); }
    }
    private record Holding(int itemId, String inventoryType, String fingerprint, int quantity,
                           Map<String, Object> attributes) { }
}
