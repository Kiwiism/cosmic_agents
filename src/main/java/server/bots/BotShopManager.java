package server.bots;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.Shop;
import server.ShopFactory;
import server.ShopItem;
import server.StatEffect;
import server.life.NPC;
import server.maps.Foothold;
import server.maps.MapObject;
import server.maps.MapObjectType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BotShopManager {

    private static final int SHOP_MANHATTAN_RADIUS = 200;
    private static final int SHOP_ARRIVE_DIST = 50;
    private static final int SHOP_NPC_SEARCH_DIST = 601;
    private static final int SHOP_APPROACH_DELAY_MAX_MS = 5001;
    private static final int SHOP_STEP_DELAY_MIN_MS = 2000;
    private static final int SHOP_STEP_DELAY_MAX_MS = 4001;
    private static final long SHOP_VISIT_TIMEOUT_MS = 30_000L;
    private static final long SHOP_SEQUENCE_TIMEOUT_MS = 45_000L;
    private static final int POT_TRIGGER_THRESHOLD = 4; // 80% of target (5) for early trigger
    private static final int POT_TARGET_THRESHOLD = 5; // full target when buying at shop
    private static final int AMMO_TRIGGER_THRESHOLD = 8;
    private static final int AMMO_TARGET_THRESHOLD = 10; // full target when buying at shop

    private BotShopManager() {}

    private record NpcShopMatch(NPC npc, Shop shop, Point npcPos) {}

    private enum ShortfallReason { NONE, NO_MESO, NO_SPACE, OTHER }

    private record BuyReport(int itemId, int quantity, int requestedQuantity, ShortfallReason reason) {
        boolean hasShortfall() {
            return reason != ShortfallReason.NONE && quantity < requestedQuantity;
        }
    }

    private record ShopSlotItem(short slot, ShopItem shopItem) {}

    private record PurchaseSequence(BotEntry entry,
                                    Character bot,
                                    Point npcPos,
                                    List<PurchaseAction> actions,
                                    List<String> bought,
                                    BuyReport firstShortfall) {
        PurchaseSequence withFirstShortfall(BuyReport report) {
            if (firstShortfall == null && report != null && report.hasShortfall()) {
                return new PurchaseSequence(entry, bot, npcPos, actions, bought, report);
            }
            return this;
        }
    }

    @FunctionalInterface
    private interface PurchaseAction {
        PurchaseSequence run(PurchaseSequence sequence, Shop shop);
    }

    private static final List<String> RESUPPLY_MSGS = List.of(
            "brb gotta resupply", "one sec, going to restock", "be right back, need supplies",
            "brb, refilling", "be right back~"
    );

    private static final List<String> SHOPPING_MSGS = List.of(
            "shopping...", "restocking now", "buying stuff", "ok let me buy", "getting supplies"
    );

    static void onMapChange(BotEntry entry, Character bot) {
        clearShopState(entry);

        NpcShopMatch match = findBestShop(bot);
        if (match == null) {
            return;
        }

        WeaponType wt = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean needsRecharge = isRechargeWeaponType(wt) && hasRechargeableInShop(bot);
        int ammoTrigger = BotCombatManager.cfg.AMMO_LOW_WARN * AMMO_TRIGGER_THRESHOLD;
        boolean needsAmmoForShop = needsAmmo(bot, wt) && BotCombatManager.countAmmo(bot, wt) < ammoTrigger && findAmmoItem(match.shop, wt) != null;
        int[] pots = BotPotionManager.countPotions(bot);
        int potTrigger = BotManager.cfg.POT_LOW_WARN * POT_TRIGGER_THRESHOLD;
        boolean needsHpPots = pots[0] < potTrigger && findPotionItem(match.shop, bot, true) != null;
        boolean needsMpPots = pots[1] < potTrigger && findPotionItem(match.shop, bot, false) != null;
        if (!needsRecharge && !needsAmmoForShop && !needsHpPots && !needsMpPots) {
            return;
        }

        long distSq = (long) bot.getPosition().distanceSq(match.npcPos);
        if (distSq > 1000L * 1000L) {
            BotManager.getInstance().botSay(bot, BotManager.randomReply(RESUPPLY_MSGS));
        }

        entry.shopVisitPending = true;
        entry.shopNpcPos = match.npcPos;
        entry.shopTargetPos = pickShopApproachPoint(match.npcPos, entry, bot);
        entry.shopApproachDelayMs = (int) BotManager.randMs(0, SHOP_APPROACH_DELAY_MAX_MS);
        entry.shopVisitStartedAtMs = System.currentTimeMillis();
        entry.shopSequenceStartedAtMs = 0L;
    }

    static boolean tickShopVisit(BotEntry entry, Character bot) {
        if (!entry.shopVisitPending) {
            return false;
        }
        if (entry.shopNpcPos == null) {
            clearShopState(entry);
            return false;
        }
        long now = System.currentTimeMillis();
        if (entry.shopVisitStartedAtMs > 0 && now - entry.shopVisitStartedAtMs > SHOP_VISIT_TIMEOUT_MS) {
            if (!entry.shopSequenceActive && entry.shopNpcPos != null && findNpcNear(bot, entry.shopNpcPos) != null) {
                entry.shopTargetPos = bot.getPosition();
                entry.shopApproachDelayMs = 0;
            } else {
                BotManager.getInstance().botSay(bot, "couldn't reach shop in time");
                clearShopState(entry);
                return false;
            }
        }
        if (entry.shopSequenceActive
                && entry.shopSequenceStartedAtMs > 0
                && now - entry.shopSequenceStartedAtMs > SHOP_SEQUENCE_TIMEOUT_MS) {
            clearShopState(entry);
            return false;
        }
        if (entry.shopApproachDelayMs > 0) {
            entry.shopApproachDelayMs = BotMovementManager.tickDown(entry.shopApproachDelayMs);
            return false;
        }

        Point botPos = bot.getPosition();
        Point target = entry.shopTargetPos != null ? entry.shopTargetPos : entry.shopNpcPos;
        if (botPos.distanceSq(target) <= (long) SHOP_ARRIVE_DIST * SHOP_ARRIVE_DIST) {
            if (!entry.shopSequenceActive) {
                entry.shopSequenceActive = true;
                entry.shopSequenceStartedAtMs = System.currentTimeMillis();
                BotManager.getInstance().botSay(bot, BotManager.randomReply(SHOPPING_MSGS));
                Point npcPos = entry.shopNpcPos;
                scheduleShopStep(entry, () -> executePurchases(entry, bot, npcPos));
            }
            return true;
        }

        return true;
    }

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
        if (needsAmmo(bot, wt) && findAmmoItem(shop, wt) != null) {
            return true;
        }
        if (isRechargeWeaponType(wt) && hasRechargeableInShop(bot)) {
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

    private static void executePurchases(BotEntry entry, Character bot, Point npcPos) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            clearShopState(entry);
            return;
        }

        WeaponType wt = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        List<PurchaseAction> actions = new ArrayList<>();

        if (isRechargeWeaponType(wt)) {
            actions.add((sequence, shop) -> {
                BuyReport recharge = doRecharge(bot, shop);
                if (recharge.quantity() > 0) {
                    int recharged = recharge.quantity();
                    String ammoName = wt == WeaponType.GUN ? "bullets" : "throwing stars";
                    sequence.bought().add("refilled " + recharged + " set"
                            + (recharged > 1 ? "s" : "") + " of my " + ammoName);
                }
                return sequence.withFirstShortfall(recharge);
            });
        }
        if (needsAmmo(bot, wt)) {
            actions.add((sequence, shop) -> appendBuyReport(sequence, buyAmmo(bot, shop, wt), "ammo"));
        }
        actions.add((sequence, shop) -> {
            int[] pots = BotPotionManager.countPotions(bot);
            if (pots[0] < BotManager.cfg.POT_LOW_WARN * 5) {
                return appendBuyReport(sequence, buyPotions(bot, shop, true), "HP pots");
            }
            return sequence;
        });
        actions.add((sequence, shop) -> {
            int[] pots = BotPotionManager.countPotions(bot);
            if (pots[1] < BotManager.cfg.POT_LOW_WARN * 5) {
                return appendBuyReport(sequence, buyPotions(bot, shop, false), "MP pots");
            }
            return sequence;
        });

        runPurchaseStep(new PurchaseSequence(entry, bot, npcPos, actions, new ArrayList<>(), null), 0);
    }

    private static void runPurchaseStep(PurchaseSequence sequence, int index) {
        if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
            clearShopState(sequence.entry());
            return;
        }
        if (index >= sequence.actions().size()) {
            finishPurchaseSequence(sequence);
            return;
        }

        NPC npc = findNpcNear(sequence.bot(), sequence.npcPos());
        if (npc == null) {
            clearShopState(sequence.entry());
            return;
        }
        Shop shop = ShopFactory.getInstance().getShopForNPC(npc.getId());
        if (shop == null) {
            clearShopState(sequence.entry());
            return;
        }

        PurchaseSequence next = sequence.actions().get(index).run(sequence, shop);
        scheduleShopStep(sequence.entry(), () -> runPurchaseStep(next, index + 1));
    }

    private static void finishPurchaseSequence(PurchaseSequence sequence) {
        if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
            clearShopState(sequence.entry());
            return;
        }

        Runnable finish = () -> {
            if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
                clearShopState(sequence.entry());
                return;
            }
            if (sequence.firstShortfall() != null) {
                BotManager.getInstance().botSay(sequence.bot(), buildShortfallMessage(sequence.firstShortfall()));
            }
            clearShopState(sequence.entry());
        };

        if (!sequence.bought().isEmpty()) {
            BotManager.getInstance().botSay(sequence.bot(), "bought " + String.join(", ", sequence.bought()));
            BotPotionManager.setupAutopotForBot(sequence.bot());
            BotCombatManager.tickAmmoCheck(sequence.entry(), sequence.bot());
            scheduleShopStep(sequence.entry(), finish);
            return;
        }

        finish.run();
    }

    private static PurchaseSequence appendBuyReport(PurchaseSequence sequence, BuyReport report, String fallbackName) {
        if (report.quantity() > 0) {
            sequence.bought().add(report.quantity() + " " + resolveItemName(report.itemId(), fallbackName));
        }
        return sequence.withFirstShortfall(report);
    }

    private static boolean needsAmmo(Character bot, WeaponType wt) {
        if (wt == null) {
            return false;
        }
        return wt == WeaponType.BOW || wt == WeaponType.CROSSBOW;
    }

    private static boolean isRechargeWeaponType(WeaponType wt) {
        return wt == WeaponType.CLAW || wt == WeaponType.GUN;
    }

    private static ShopSlotItem findAmmoItem(Shop shop, WeaponType wt) {
        List<ShopItem> items = shop.getItems();
        ShopSlotItem best = null;
        for (int i = 0; i < items.size(); i++) {
            ShopItem si = items.get(i);
            if (si.getPrice() <= 0) {
                continue;
            }
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
        ShopSlotItem ammo = findAmmoItem(shop, wt);
        if (ammo == null) {
            return new BuyReport(0, 0, 0, ShortfallReason.NONE);
        }

        int target = BotCombatManager.cfg.AMMO_LOW_WARN * AMMO_TARGET_THRESHOLD;
        int current = BotCombatManager.countAmmo(bot, wt);
        return buyFixedCostItem(bot, shop, ammo, Math.max(0, target - current), 1000);
    }

    private static boolean hasRechargeableInShop(Character bot) {
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (ItemConstants.isRechargeable(item.getItemId())) {
                short slotMax = ItemInformationProvider.getInstance().getSlotMax(bot.getClient(), item.getItemId());
                if (item.getQuantity() < slotMax * 4 / 5) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BuyReport doRecharge(Character bot, Shop shop) {
        int recharged = 0;
        int attempted = 0;
        int shortfallItemId = 0;
        ShortfallReason reason = ShortfallReason.NONE;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (!ItemConstants.isRechargeable(item.getItemId())) {
                continue;
            }
            short slotMax = ItemInformationProvider.getInstance().getSlotMax(bot.getClient(), item.getItemId());
            if (item.getQuantity() >= slotMax) {
                continue;
            }

            Shop.TransactionResult result = shop.rechargeDirect(bot, item.getPosition());
            if (result == Shop.TransactionResult.SUCCESS) {
                recharged++;
                attempted++;
                continue;
            }
            attempted++;
            shortfallItemId = item.getItemId();
            reason = switch (result) {
                case NOT_ENOUGH_MESO -> ShortfallReason.NO_MESO;
                case NO_SPACE -> ShortfallReason.NO_SPACE;
                default -> ShortfallReason.OTHER;
            };
            break;
        }
        return new BuyReport(shortfallItemId, recharged, attempted, reason);
    }

    private static ShopSlotItem findPotionItem(Shop shop, Character bot, boolean forHp) {
        List<ShopItem> items = shop.getItems();
        int maxStat = forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp();
        int minRecover = (int) (maxStat * 0.10);
        int maxRecover = (int) (maxStat * 0.50);

        ShopSlotItem best = null;
        for (int i = 0; i < items.size(); i++) {
            ShopItem si = items.get(i);
            if (si.getPrice() <= 0) {
                continue;
            }
            int id = si.getItemId();
            if (!BotInventoryManager.isRecoveryPotion(id)) {
                continue;
            }

            StatEffect fx = BotInventoryManager.itemEffect(id);
            if (fx == null) {
                continue;
            }
            if (forHp && fx.getHpRate() > 0) {
                continue;
            }
            if (!forHp && fx.getMpRate() > 0) {
                continue;
            }

            int recover = forHp ? fx.getHp() : fx.getMp();
            if (recover <= 0 || recover < minRecover || recover > maxRecover) {
                continue;
            }

            if (best == null || si.getPrice() < best.shopItem.getPrice()) {
                best = new ShopSlotItem((short) i, si);
            }
        }
        return best;
    }

    private static BuyReport buyPotions(Character bot, Shop shop, boolean forHp) {
        ShopSlotItem pot = findPotionItem(shop, bot, forHp);
        if (pot == null) {
            return new BuyReport(0, 0, 0, ShortfallReason.NONE);
        }

        int target = BotManager.cfg.POT_LOW_WARN * POT_TARGET_THRESHOLD;
        int[] pots = BotPotionManager.countPotions(bot);
        int current = forHp ? pots[0] : pots[1];
        return buyFixedCostItem(bot, shop, pot, Math.max(0, target - current), 100);
    }

    private static BuyReport buyFixedCostItem(Character bot, Shop shop, ShopSlotItem item, int desiredQuantity, int batchSize) {
        if (item == null || desiredQuantity <= 0) {
            return new BuyReport(0, 0, 0, ShortfallReason.NONE);
        }

        int totalBought = 0;
        ShortfallReason reason = ShortfallReason.NONE;
        int price = item.shopItem.getPrice();

        while (totalBought < desiredQuantity) {
            int remaining = desiredQuantity - totalBought;
            short qty = (short) Math.min(remaining, batchSize);
            Shop.TransactionResult result = shop.buyDirect(bot, item.slot, item.shopItem.getItemId(), qty);
            if (result == Shop.TransactionResult.SUCCESS) {
                totalBought += qty;
                continue;
            }
            if (result == Shop.TransactionResult.NOT_ENOUGH_MESO) {
                reason = ShortfallReason.NO_MESO;
                int affordable = price > 0 ? Math.min(remaining, bot.getMeso() / price) : 0;
                if (affordable > 0) {
                    Shop.TransactionResult partial = shop.buyDirect(bot, item.slot, item.shopItem.getItemId(), (short) affordable);
                    if (partial == Shop.TransactionResult.SUCCESS) {
                        totalBought += affordable;
                    } else if (partial == Shop.TransactionResult.NO_SPACE) {
                        reason = ShortfallReason.NO_SPACE;
                    }
                }
            } else if (result == Shop.TransactionResult.NO_SPACE) {
                reason = ShortfallReason.NO_SPACE;
            } else {
                reason = ShortfallReason.OTHER;
            }
            break;
        }

        return new BuyReport(item.shopItem.getItemId(), totalBought, desiredQuantity, reason);
    }

    private static String buildShortfallMessage(BuyReport report) {
        String itemName = resolveItemName(report.itemId(), "item");
        String got = GameConstants.numberWithCommas(report.quantity());
        String want = GameConstants.numberWithCommas(report.requestedQuantity());
        return switch (report.reason()) {
            case NO_SPACE -> report.quantity() <= 0
                    ? "no room in my bag for " + itemName
                    : "only fit " + got + " " + itemName + " out of " + want + " — bag's full";
            case OTHER -> "shop wouldn't sell me " + itemName;
            case NO_MESO, NONE -> report.quantity() <= 0
                    ? "couldn't afford any " + itemName + " this trip"
                    : "could only afford " + got + " " + itemName + " out of " + want;
        };
    }

    private static String resolveItemName(int itemId, String fallbackName) {
        String name = ItemInformationProvider.getInstance().getName(itemId);
        return name != null ? name : fallbackName;
    }

    private static boolean isShopSequenceValid(BotEntry entry, Character bot, Point npcPos) {
        return entry.shopVisitPending
                && entry.shopSequenceActive
                && npcPos != null
                && bot.getMap() != null
                && bot.getPosition().distanceSq(entry.shopTargetPos != null ? entry.shopTargetPos : npcPos) <= (long) SHOP_ARRIVE_DIST * SHOP_ARRIVE_DIST
                && findNpcNear(bot, npcPos) != null;
    }

    static void cancelShopVisit(BotEntry entry) {
        clearShopState(entry);
    }

    private static void clearShopState(BotEntry entry) {
        entry.shopVisitPending = false;
        entry.shopNpcPos = null;
        entry.shopTargetPos = null;
        entry.shopApproachDelayMs = 0;
        entry.shopSequenceActive = false;
        entry.shopVisitStartedAtMs = 0L;
        entry.shopSequenceStartedAtMs = 0L;
    }

    private static long stepDelayMs() {
        return BotManager.randMs(SHOP_STEP_DELAY_MIN_MS, SHOP_STEP_DELAY_MAX_MS);
    }

    private static void scheduleShopStep(BotEntry entry, Runnable step) {
        BotManager.after(stepDelayMs(), () -> {
            if (!entry.shopVisitPending) {
                return;
            }
            try {
                step.run();
            } catch (RuntimeException exception) {
                clearShopState(entry);
                throw exception;
            }
        });
    }

    private static Point pickShopApproachPoint(Point npcPos, BotEntry entry, Character bot) {
        var footholds = bot.getMap().getFootholds();
        if (footholds == null) {
            return npcPos;
        }
        List<Point> candidates = new ArrayList<>();
        for (Foothold fh : footholds.getAllFootholds()) {
            int fx1 = fh.getX1(), fy1 = fh.getY1();
            int fx2 = fh.getX2(), fy2 = fh.getY2();
            if (fx1 == fx2) {
                continue; // wall foothold
            }
            int xMin = Math.min(fx1, fx2);
            int xMax = Math.max(fx1, fx2);
            int step = Math.max(1, (xMax - xMin) / 20);
            for (int x = xMin; x <= xMax; x += step) {
                double t = (double) (x - fx1) / (fx2 - fx1);
                int y = (int) (fy1 + t * (fy2 - fy1));
                if (Math.abs(x - npcPos.x) + Math.abs(y - npcPos.y) <= SHOP_MANHATTAN_RADIUS) {
                    candidates.add(new Point(x, y));
                }
            }
        }
        if (candidates.isEmpty()) {
            return npcPos;
        }
        BotMovementProfile profile = entry.movementProfile != null
                ? entry.movementProfile : BotMovementProfile.fromCharacter(bot);
        BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(bot.getMap(), profile);
        if (graph == null) {
            graph = BotNavigationGraphProvider.peekClosestGraph(bot.getMap(), profile);
        }
        if (graph != null) {
            Point botPos = bot.getPosition();
            int startRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            if (startRegionId >= 0) {
                List<Point> reachable = new ArrayList<>();
                for (Point candidate : candidates) {
                    int targetRegionId = BotNavigationManager.resolveTargetRegionId(
                            graph, entry, bot.getMap(), candidate);
                    if (targetRegionId < 0) continue;
                    if (startRegionId == targetRegionId
                            || !BotNavigationManager.findPath(graph, bot.getMap(), botPos,
                                    startRegionId, targetRegionId, candidate).isEmpty()) {
                        reachable.add(candidate);
                    }
                }
                if (!reachable.isEmpty()) {
                    candidates = reachable;
                }
            }
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static NPC findNpcNear(Character bot, Point pos) {
        for (MapObject obj : bot.getMap().getMapObjectsInRange(
                pos, SHOP_NPC_SEARCH_DIST * SHOP_NPC_SEARCH_DIST,
                Arrays.asList(MapObjectType.NPC))) {
            NPC npc = (NPC) obj;
            if (npc.hasShop()) {
                return npc;
            }
        }
        return null;
    }
}
