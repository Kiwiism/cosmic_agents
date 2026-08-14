package server.agents.economy.integration.cosmic;

import constants.id.ItemId;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.Shop;
import server.ShopFactory;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.ItemCategory;
import server.agents.economy.catalog.ItemFact;
import server.agents.economy.catalog.MonsterDropFact;
import server.agents.economy.catalog.MonsterFact;
import server.agents.economy.catalog.GlobalDropFact;
import server.agents.economy.catalog.EquipmentRollFact;
import client.inventory.Equip;
import server.agents.economy.catalog.NpcShopFact;
import server.life.MonsterInformationProvider;
import server.life.LifeFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.DoubleSupplier;
import java.util.LinkedHashMap;

/** Live adapter over the same WZ/SQL loaders and predicates used by normal gameplay. */
public final class CosmicEconomyCatalog implements EconomyCatalog {
    private final String version;
    private final ItemInformationProvider items;
    private final MonsterInformationProvider drops;
    private final ShopFactory shops;
    private final IntFunction<Integer> npcSourceMap;

    public CosmicEconomyCatalog(String version, IntFunction<Integer> npcSourceMap) {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("catalog version is required");
        this.version = version;
        this.items = ItemInformationProvider.getInstance();
        this.drops = MonsterInformationProvider.getInstance();
        this.shops = ShopFactory.getInstance();
        this.npcSourceMap = npcSourceMap;
    }

    @Override
    public String version() { return version; }

    @Override
    public Optional<ItemFact> item(int itemId) {
        String name = items.getName(itemId);
        if (name == null) return Optional.empty();
        EnumSet<ItemCategory> categories = EnumSet.noneOf(ItemCategory.class);
        if (ItemConstants.isEquipment(itemId)) categories.add(ItemCategory.EQUIPMENT);
        if (ItemConstants.isEquipScroll(itemId)) categories.add(ItemCategory.EQUIP_SCROLL);
        if (ItemConstants.isPotion(itemId)) categories.add(ItemCategory.POTION);
        if (ItemConstants.isFood(itemId)) categories.add(ItemCategory.FOOD);
        if (ItemConstants.isThrowingStar(itemId)) categories.add(ItemCategory.THROWING_STAR);
        if (ItemConstants.isBullet(itemId)) categories.add(ItemCategory.BULLET);
        if (ItemConstants.isArrow(itemId)) categories.add(ItemCategory.ARROW);
        if (ItemId.isChair(itemId)) categories.add(ItemCategory.CHAIR);
        if (items.isQuestItem(itemId)) categories.add(ItemCategory.QUEST_ITEM);
        if (items.isCash(itemId)) categories.add(ItemCategory.CASH);
        if (categories.isEmpty()) categories.add(ItemCategory.OTHER);
        Integer requiredLevel = ItemConstants.isEquipment(itemId) ? items.getEquipLevelReq(itemId) : null;
        return Optional.of(new ItemFact(itemId, name, Math.max(0, items.getPrice(itemId, 1)),
                requiredLevel, items.getBaseSlotMax(itemId), categories, items.getEquipStats(itemId)));
    }

    @Override
    public List<MonsterDropFact> monsterDrops(int monsterId) {
        return drops.retrieveDrop(monsterId).stream()
                .map(drop -> new MonsterDropFact(monsterId, drop.itemId, drop.chance,
                        drop.Minimum, drop.Maximum, drop.questid))
                .toList();
    }

    @Override
    public Optional<MonsterFact> monster(int monsterId) {
        var monster = LifeFactory.getMonster(monsterId);
        return monster == null ? Optional.empty()
                : Optional.of(new MonsterFact(monsterId, monster.getLevel(), monster.getExp()));
    }

    @Override
    public List<GlobalDropFact> globalDrops(int mapId) {
        return drops.getRelevantGlobalDrops(mapId).stream()
                .map(drop -> new GlobalDropFact(drop.itemId, drop.chance, drop.continentid,
                        drop.Minimum, drop.Maximum, drop.questid)).toList();
    }

    @Override
    public Optional<EquipmentRollFact> rollEquipment(int itemId, DoubleSupplier random) {
        if (!ItemConstants.isEquipment(itemId)) return Optional.empty();
        Equip equip = items.randomizeStats((Equip) items.getEquipById(itemId), true, random);
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("upgradeSlots", (int) equip.getUpgradeSlots()); stats.put("level", (int) equip.getLevel());
        stats.put("STR", (int) equip.getStr()); stats.put("DEX", (int) equip.getDex());
        stats.put("INT", (int) equip.getInt()); stats.put("LUK", (int) equip.getLuk());
        stats.put("MHP", (int) equip.getHp()); stats.put("MMP", (int) equip.getMp());
        stats.put("PAD", (int) equip.getWatk()); stats.put("MAD", (int) equip.getMatk());
        stats.put("PDD", (int) equip.getWdef()); stats.put("MDD", (int) equip.getMdef());
        stats.put("ACC", (int) equip.getAcc()); stats.put("EVA", (int) equip.getAvoid());
        stats.put("hands", (int) equip.getHands()); stats.put("Speed", (int) equip.getSpeed());
        stats.put("Jump", (int) equip.getJump()); stats.put("vicious", (int) equip.getVicious());
        stats.put("itemLevel", (int) equip.getItemLevel()); stats.put("itemExp", equip.getItemExp());
        stats.put("ringId", equip.getRingId()); stats.put("flag", (int) equip.getFlag());
        return Optional.of(new EquipmentRollFact(itemId, stats));
    }

    @Override
    public Optional<NpcShopFact> npcShop(int npcId) {
        Shop shop = shops.getShopForNPC(npcId);
        if (shop == null) return Optional.empty();
        List<NpcShopFact.NpcShopItemFact> stock = shop.getItems().stream()
                .map(item -> new NpcShopFact.NpcShopItemFact(item.getItemId(), item.getPrice(),
                        item.getPitch(), item.getBuyable()))
                .toList();
        return Optional.of(new NpcShopFact(shop.getId(), npcId, npcSourceMap.apply(npcId), stock));
    }
}
