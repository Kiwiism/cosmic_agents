package server.maps;

import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Complete, durable representation of unsold PlayerShop inventory. */
public record PlayerShopEscrowSnapshot(String escrowId, int ownerCharacterId, int roomMapId,
                                       int spotX, int permitItemId, String description,
                                       List<Listing> listings) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public PlayerShopEscrowSnapshot {
        if (escrowId == null || escrowId.isBlank() || ownerCharacterId <= 0 || roomMapId <= 0
                || permitItemId <= 0 || description == null || listings == null) {
            throw new IllegalArgumentException("invalid player-shop escrow snapshot");
        }
        listings = List.copyOf(listings);
    }

    public static PlayerShopEscrowSnapshot capture(PlayerShop shop) {
        if (shop.getEscrowId() == null) throw new IllegalStateException("shop is not escrow managed");
        List<Listing> listings = new ArrayList<>();
        for (PlayerShopItem listing : shop.getItems()) {
            if (listing.isExist() && listing.getBundles() > 0) {
                listings.add(new Listing(listing.getBundles(), listing.getPrice(),
                        ItemState.capture(listing.getItem())));
            }
        }
        return new PlayerShopEscrowSnapshot(shop.getEscrowId(), shop.getOwnerId(), shop.getMapId(),
                shop.getPosition().x, shop.getItemId(), shop.getDescription(), listings);
    }

    public String listingsJson() {
        try {
            return JSON.writeValueAsString(listings);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not encode player-shop escrow", failure);
        }
    }

    public static List<Listing> decodeListings(String json) {
        try {
            return List.copyOf(JSON.readValue(json,
                    JSON.getTypeFactory().constructCollectionType(List.class, Listing.class)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not decode player-shop escrow", failure);
        }
    }

    public record Listing(short bundles, int price, ItemState item) {
        public Listing {
            if (bundles <= 0 || price <= 0 || item == null) throw new IllegalArgumentException();
        }

        public PlayerShopItem toPlayerShopItem() {
            return new PlayerShopItem(item.toItem(), bundles, price);
        }
    }

    public record ItemState(int itemId, byte inventoryType, short position, short quantity,
                            String owner, int petId, short flag, long expiration, String giftFrom,
                            int sn, Byte upgradeSlots, Byte level, Short str, Short dex, Short intelligence,
                            Short luk, Short hp, Short mp, Short watk, Short matk, Short wdef, Short mdef,
                            Short acc, Short avoid, Short hands, Short speed, Short jump, Short vicious,
                            Byte itemLevel, Integer itemExp, Integer ringId) {
        static ItemState capture(Item item) {
            if (item instanceof Equip equip) {
                return new ItemState(item.getItemId(), item.getInventoryType().getType(), item.getPosition(),
                        item.getQuantity(), item.getOwner(), item.getPetId(), item.getFlag(), item.getExpiration(),
                        item.getGiftFrom(), item.getSN(), equip.getUpgradeSlots(), equip.getLevel(), equip.getStr(),
                        equip.getDex(), equip.getInt(), equip.getLuk(), equip.getHp(), equip.getMp(), equip.getWatk(),
                        equip.getMatk(), equip.getWdef(), equip.getMdef(), equip.getAcc(), equip.getAvoid(),
                        equip.getHands(), equip.getSpeed(), equip.getJump(), equip.getVicious(), equip.getItemLevel(),
                        equip.getItemExp(), equip.getRingId());
            }
            return new ItemState(item.getItemId(), item.getInventoryType().getType(), item.getPosition(),
                    item.getQuantity(), item.getOwner(), item.getPetId(), item.getFlag(), item.getExpiration(),
                    item.getGiftFrom(), item.getSN(), null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
        }

        public Item toItem() {
            InventoryType type = InventoryType.getByType(inventoryType);
            Item result;
            if (type == InventoryType.EQUIP || type == InventoryType.EQUIPPED) {
                Equip equip = Equip.restored(itemId, position);
                equip.setQuantity(quantity);
                equip.setUpgradeSlots(upgradeSlots);
                equip.setLevel(level);
                equip.setStr(str);
                equip.setDex(dex);
                equip.setInt(intelligence);
                equip.setLuk(luk);
                equip.setHp(hp);
                equip.setMp(mp);
                equip.setWatk(watk);
                equip.setMatk(matk);
                equip.setWdef(wdef);
                equip.setMdef(mdef);
                equip.setAcc(acc);
                equip.setAvoid(avoid);
                equip.setHands(hands);
                equip.setSpeed(speed);
                equip.setJump(jump);
                equip.setVicious(vicious);
                equip.setItemLevel(itemLevel);
                equip.setItemExp(itemExp);
                equip.setRingId(ringId);
                result = equip;
            } else {
                result = new Item(itemId, position, quantity, petId);
            }
            result.setOwner(owner);
            result.setFlag(flag);
            result.setExpiration(expiration);
            result.setGiftFrom(giftFrom);
            result.setSN(sn);
            return result;
        }
    }
}
