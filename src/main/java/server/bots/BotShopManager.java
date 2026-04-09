package server.bots;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.Shop;
import server.ShopFactory;
import server.ShopItem;
import server.StatEffect;
import server.bots.combat.BotAttackDataProvider;
import server.life.NPC;
import server.maps.MapObject;
import server.maps.MapObjectType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BotShopManager {

    private static final int SHOP_INTERACT_DIST = 400;
    private static final int SHOP_BUY_DELAY_BASE_MS = 1500;
    private static final int SHOP_BUY_DELAY_RAND_MS = 1000;

    private BotShopManager() {}

    // ── Map-change trigger ──────────────────────────────────────────────

    /**
     * Called once per map change. Finds a shop NPC on the map that sells
     * something the bot needs, and sets a movement target toward it.
     * The actual buying happens when the bot is within interact distance.
     */
    static void onMapChange(BotEntry entry, Character bot) {
        entry.shopVisitPending = false;
        entry.shopNpcPos = null;

        NpcShopMatch match = findBestShop(bot);
        if (match == null) {
            return;
        }

        entry.shopVisitPending = true;
        entry.shopNpcPos = match.npcPos;
    }

    /**
     * Called from the tick loop when shopVisitPending is true.
     * Returns true if the bot should navigate toward the shop NPC this tick.
     */
    static boolean tickShopVisit(BotEntry entry, Character bot) {
        if (!entry.shopVisitPending) {
            return false;
        }
        if (entry.shopNpcPos == null) {
            entry.shopVisitPending = false;
            return false;
        }

        Point botPos = bot.getPosition();
        if (botPos.distanceSq(entry.shopNpcPos) <= (long) SHOP_INTERACT_DIST * SHOP_INTERACT_DIST) {
            // Close enough — schedule the buy with a believable delay
            entry.shopVisitPending = false;
            Point npcPos = entry.shopNpcPos;
            entry.shopNpcPos = null;
            BotManager.after(BotManager.randMs(SHOP_BUY_DELAY_BASE_MS,
                    SHOP_BUY_DELAY_BASE_MS + SHOP_BUY_DELAY_RAND_MS),
                    () -> executePurchases(entry, bot, npcPos));
            return false;
        }

        return true; // still navigating
    }

    // ── Shop discovery ──────────────────────────────────────────────────

    private record NpcShopMatch(NPC npc, Shop shop, Point npcPos) {}

    private static NpcShopMatch findBestShop(Character bot) {
        List<MapObject> objects = bot.getMap().getMapObjectsInRange(
                new Point(0, 0), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.NPC));

        for (MapObject obj : objects) {
            NPC npc = (NPC) obj;
            if (!npc.hasShop()) {
                continue;
            }
            Shop shop = ShopFactory.getInstance().getShopForNPC(npc.getId());
            if (shop == null) {
                continue;
            }
            if (shopHasAnythingNeeded(bot, shop)) {
                return new NpcShopMatch(npc, shop, npc.getPosition());
            }
        }
        return null;
    }

    private static boolean shopHasAnythingNeeded(Character bot, Shop shop) {
        WeaponType wt = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (needsAmmo(bot, wt) && findAmmoItem(shop, wt, bot) != null) {
            return true;
        }
        if (isRechargeWeaponType(wt) && hasRechargeableInShop(shop, bot)) {
            return true;
        }
        int[] pots = BotPotionManager.countPotions(bot);
        if (pots[0] < BotManager.cfg.POT_LOW_WARN * 5 && findPotionItem(shop, bot, true) != null) {
            return true;
        }
        if (pots[1] < BotManager.cfg.POT_LOW_WARN * 5 && findPotionItem(shop, bot, false) != null) {
            return true;
        }
        return false;
    }

    // ── Purchase execution ──────────────────────────────────────────────

    private static void executePurchases(BotEntry entry, Character bot, Point npcPos) {
        // Re-find the shop NPC (may have despawned etc.)
        NPC npc = findNpcNear(bot, npcPos);
        if (npc == null) {
            return;
        }
        Shop shop = ShopFactory.getInstance().getShopForNPC(npc.getId());
        if (shop == null) {
            return;
        }

        List<String> bought = new ArrayList<>();
        boolean brokeOnce = false;

        // Priority: ammo > hp pots > mp pots
        WeaponType wt = BotAttackExecutionProvider.getEquippedWeaponType(bot);

        // 1. Recharge existing star/bullet stacks
        if (isRechargeWeaponType(wt)) {
            int recharged = doRecharge(bot, shop);
            if (recharged > 0) {
                bought.add("recharged " + recharged + " stack" + (recharged > 1 ? "s" : ""));
            }
        }

        // 2. Buy arrows/bullets (non-rechargeable ammo)
        if (needsAmmo(bot, wt)) {
            BuyReport report = buyAmmo(bot, shop, wt);
            if (report.quantity > 0) {
                String name = ItemInformationProvider.getInstance().getName(report.itemId);
                bought.add(report.quantity + " " + (name != null ? name : "ammo"));
            }
            if (report.broke) brokeOnce = true;
        }

        // 3. HP potions, 4. MP potions
        int[] pots = BotPotionManager.countPotions(bot);
        if (pots[0] < BotManager.cfg.POT_LOW_WARN * 5) {
            BuyReport report = buyPotions(bot, shop, true);
            if (report.quantity > 0) {
                String name = ItemInformationProvider.getInstance().getName(report.itemId);
                bought.add(report.quantity + " " + (name != null ? name : "HP pots"));
            }
            if (report.broke) brokeOnce = true;
        }
        if (pots[1] < BotManager.cfg.POT_LOW_WARN * 5) {
            BuyReport report = buyPotions(bot, shop, false);
            if (report.quantity > 0) {
                String name = ItemInformationProvider.getInstance().getName(report.itemId);
                bought.add(report.quantity + " " + (name != null ? name : "MP pots"));
            }
            if (report.broke) brokeOnce = true;
        }

        // Report
        if (!bought.isEmpty()) {
            BotManager.getInstance().botSay(bot, "bought " + String.join(", ", bought));
            // Refresh autopot bindings after buying pots
            BotPotionManager.setupAutopotForBot(bot);
            // Clear ammo flags since we just restocked
            BotCombatManager.tickAmmoCheck(entry, bot);
        }
        if (brokeOnce) {
            BotManager.after(BotManager.randMs(2000, 3000), () -> {
                BotManager.getInstance().botSay(bot, "not enough mesos to buy everything i need");
            });
        }
    }

    // ── Ammo buying ─────────────────────────────────────────────────────

    private record BuyReport(int itemId, int quantity, boolean broke) {}

    private static boolean needsAmmo(Character bot, WeaponType wt) {
        if (wt == null) return false;
        // Only non-rechargeable ammo: arrows
        return wt == WeaponType.BOW || wt == WeaponType.CROSSBOW;
    }

    private static boolean isRechargeWeaponType(WeaponType wt) {
        return wt == WeaponType.CLAW || wt == WeaponType.GUN;
    }

    private record ShopSlotItem(short slot, ShopItem shopItem) {}

    /** Find the cheapest ammo item matching the weapon type. */
    private static ShopSlotItem findAmmoItem(Shop shop, WeaponType wt, Character bot) {
        List<ShopItem> items = shop.getItems();
        ShopSlotItem best = null;
        for (int i = 0; i < items.size(); i++) {
            ShopItem si = items.get(i);
            if (si.getPrice() <= 0) continue;
            int id = si.getItemId();
            boolean matches = switch (wt) {
                case BOW -> ItemConstants.isArrowForBow(id);
                case CROSSBOW -> ItemConstants.isArrowForCrossBow(id);
                default -> false;
            };
            if (matches && (best == null || si.getPrice() < best.shopItem.getPrice())) {
                best = new ShopSlotItem((short) i, si);
            }
        }
        return best;
    }

    private static BuyReport buyAmmo(Character bot, Shop shop, WeaponType wt) {
        ShopSlotItem ammo = findAmmoItem(shop, wt, bot);
        if (ammo == null) return new BuyReport(0, 0, false);

        int target = BotCombatManager.cfg.AMMO_LOW_WARN * 5;
        int current = BotCombatManager.countAmmo(bot, wt);
        int totalBought = 0;
        boolean broke = false;

        while (current < target) {
            short qty = (short) Math.min(target - current, 1000); // buy in chunks
            Shop.TransactionResult result = shop.buyDirect(bot, ammo.slot, ammo.shopItem.getItemId(), qty);
            if (result == Shop.TransactionResult.NOT_ENOUGH_MESO) {
                broke = true;
                break;
            }
            if (result != Shop.TransactionResult.SUCCESS) break;
            totalBought += qty;
            current += qty;
        }
        return new BuyReport(ammo.shopItem.getItemId(), totalBought, broke);
    }

    // ── Recharge ────────────────────────────────────────────────────────

    private static boolean hasRechargeableInShop(Shop shop, Character bot) {
        // Recharge doesn't require the shop to sell the item — any shop can recharge.
        // Check if bot has rechargeable items that need refilling.
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (ItemConstants.isRechargeable(item.getItemId())) {
                short slotMax = ItemInformationProvider.getInstance().getSlotMax(bot.getClient(), item.getItemId());
                if (item.getQuantity() < slotMax) return true;
            }
        }
        return false;
    }

    private static int doRecharge(Character bot, Shop shop) {
        int recharged = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (!ItemConstants.isRechargeable(item.getItemId())) continue;
            short slotMax = ItemInformationProvider.getInstance().getSlotMax(bot.getClient(), item.getItemId());
            if (item.getQuantity() >= slotMax) continue;

            Shop.TransactionResult result = shop.rechargeDirect(bot, item.getPosition());
            if (result == Shop.TransactionResult.SUCCESS) recharged++;
            if (result == Shop.TransactionResult.NOT_ENOUGH_MESO) break;
        }
        return recharged;
    }

    // ── Potion buying ───────────────────────────────────────────────────

    /**
     * Find the cheapest potion that recovers ≥10% and ≤50% of max HP/MP.
     * Never buy % recovery or buff potions.
     */
    private static ShopSlotItem findPotionItem(Shop shop, Character bot, boolean forHp) {
        List<ShopItem> items = shop.getItems();
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int maxStat = forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp();
        int minRecover = (int) (maxStat * 0.10);
        int maxRecover = (int) (maxStat * 0.50);

        ShopSlotItem best = null;
        for (int i = 0; i < items.size(); i++) {
            ShopItem si = items.get(i);
            if (si.getPrice() <= 0) continue;
            int id = si.getItemId();
            if (!BotInventoryManager.isRecoveryPotion(id)) continue;

            StatEffect fx = BotInventoryManager.itemEffect(id);
            if (fx == null) continue;
            // Never buy % recovery potions
            if (forHp && fx.getHpRate() > 0) continue;
            if (!forHp && fx.getMpRate() > 0) continue;

            int recover = forHp ? fx.getHp() : fx.getMp();
            if (recover <= 0) continue;
            if (recover < minRecover || recover > maxRecover) continue;

            if (best == null || si.getPrice() < best.shopItem.getPrice()) {
                best = new ShopSlotItem((short) i, si);
            }
        }
        return best;
    }

    private static BuyReport buyPotions(Character bot, Shop shop, boolean forHp) {
        ShopSlotItem pot = findPotionItem(shop, bot, forHp);
        if (pot == null) return new BuyReport(0, 0, false);

        int target = BotManager.cfg.POT_LOW_WARN * 5;
        int[] pots = BotPotionManager.countPotions(bot);
        int current = forHp ? pots[0] : pots[1];
        int totalBought = 0;
        boolean broke = false;

        while (current < target) {
            short qty = (short) Math.min(target - current, 100); // buy in batches
            Shop.TransactionResult result = shop.buyDirect(bot, pot.slot, pot.shopItem.getItemId(), qty);
            if (result == Shop.TransactionResult.NOT_ENOUGH_MESO) {
                broke = true;
                break;
            }
            if (result != Shop.TransactionResult.SUCCESS) break;
            totalBought += qty;
            current += qty;
        }
        return new BuyReport(pot.shopItem.getItemId(), totalBought, broke);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static NPC findNpcNear(Character bot, Point pos) {
        for (MapObject obj : bot.getMap().getMapObjectsInRange(
                pos, SHOP_INTERACT_DIST * SHOP_INTERACT_DIST,
                Arrays.asList(MapObjectType.NPC))) {
            NPC npc = (NPC) obj;
            if (npc.hasShop()) return npc;
        }
        return null;
    }
}
