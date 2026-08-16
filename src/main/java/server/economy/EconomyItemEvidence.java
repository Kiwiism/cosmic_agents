package server.economy;

import client.inventory.Equip;
import client.inventory.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Canonical item description shared by Cosmic mutation and bootstrap evidence. */
public final class EconomyItemEvidence {
    private static final ObjectMapper JSON = new ObjectMapper();
    private EconomyItemEvidence() { }

    public static Description describe(Item item) {
        Map<String, Object> attributes = attributes(item);
        return new Description(fingerprint(item.getItemId(), attributes), attributes);
    }

    public static String fingerprint(int itemId, Map<String, Object> attributes) {
        try {
            String canonical = itemId + "|" + JSON.writeValueAsString(new TreeMap<>(attributes));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
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

    public record Description(String fingerprint, Map<String, Object> attributes) {
        public Description { attributes = Map.copyOf(attributes); }
    }
}
