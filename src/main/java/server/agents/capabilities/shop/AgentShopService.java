package server.agents.capabilities.shop;

import server.agents.capabilities.movement.AgentMovementTimers;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.shop.AgentShopAmmoPolicy;
import server.agents.capabilities.shop.AgentShopApproachPolicy;
import server.agents.capabilities.shop.AgentShopPotionPolicy;
import server.agents.capabilities.supplies.AgentPotionService;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotShopRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.agents.integration.AgentBotCombatAmmoCheckRuntime;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.Shop;
import server.ShopFactory;
import server.ShopItem;
import server.bots.BotEntry;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.life.NPC;
import server.maps.MapObject;
import server.maps.MapObjectType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

public final class AgentShopService {

    // Test seam: ItemInformationProvider's WZ/DB static initializer can't run in unit tests,
    // so projectile attack / slot-max lookups go through overridable hooks (see AgentShopServiceTest).
    @FunctionalInterface
    interface SlotMaxLookup {
        short slotMax(Character bot, int itemId);
    }

    static IntUnaryOperator projectileWatk =
            id -> ItemInformationProvider.getInstance().getWatkForProjectile(id);
    static SlotMaxLookup ammoSlotMax =
            (bot, id) -> ItemInformationProvider.getInstance().getSlotMax(bot.getClient(), id);

    private static final int SHOP_MANHATTAN_RADIUS = 200;
    private static final int SHOP_ARRIVE_DIST = 100;
    private static final int SHOP_NPC_SEARCH_DIST = 601;
    private static final int SHOP_APPROACH_DELAY_MAX_MS = 5001;
    private static final int SHOP_STEP_DELAY_MIN_MS = 2000;
    private static final int SHOP_STEP_DELAY_MAX_MS = 4001;
    private static final int SELL_TRASH_STEP_DELAY_MS = 500;
    private static final long SHOP_VISIT_TIMEOUT_MS = 30_000L;
    private static final long SHOP_SEQUENCE_TIMEOUT_MS = 45_000L;
    private static final long SHOP_STUCK_FALLBACK_MS = 1000L;
    private static final int SHOP_STUCK_MOVE_TOLERANCE_PX = 2;
    private static final int POT_TRIGGER_THRESHOLD = 4; // 80% of target (5) for early trigger
    private static final int POT_TARGET_THRESHOLD = 5; // full target when buying at shop
    private static final int AMMO_TRIGGER_THRESHOLD = 8;
    private static final int AMMO_TARGET_THRESHOLD = 10; // full target when buying at shop
    private static final int RECHARGE_MAX_SETS = 10; // cap recharge to the best N own-type stacks

    private AgentShopService() {}

    private record NpcShopMatch(NPC npc, Shop shop, Point npcPos) {}

    private record ShopSlotItem(short slot, ShopItem shopItem) {}

    public static void onMapChange(AgentRuntimeEntry entry, Character bot) {
        onMapChange(asBotEntry(entry), bot);
    }

    public static void onMapChange(BotEntry entry, Character bot) {
        clearShopState(entry);

        NpcShopMatch match = findBestShop(bot, false);
        if (match == null) {
            return;
        }

        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean needsRecharge = needsRechargeForShop(bot, wt, ammoTriggerThreshold());
        boolean needsAmmoForShop = needsFixedAmmoForShop(bot, match.shop, wt, ammoTriggerThreshold());
        int[] pots = AgentPotionService.countPotions(bot);
        int potTrigger = AgentRuntimeConfig.cfg.POT_LOW_WARN * POT_TRIGGER_THRESHOLD;
        boolean needsHpPots = pots[0] < potTrigger && findPotionItem(match.shop, bot, true) != null;
        boolean needsMpPots = pots[1] < potTrigger && findPotionItem(match.shop, bot, false) != null;
        if (!needsRecharge && !needsAmmoForShop && !needsHpPots && !needsMpPots) {
            return;
        }

        long distSq = (long) bot.getPosition().distanceSq(match.npcPos);
        if (distSq > 1000L * 1000L) {
            AgentBotShopRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.shopResupplyReplies()));
        }

        startShopVisit(entry, bot, match);
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }

    public static void requestSellTrashVisit(AgentRuntimeEntry entry, Character bot) {
        requestSellTrashVisit(asBotEntry(entry), bot);
    }

    public static void requestSellTrashVisit(BotEntry entry, Character bot) {
        if (entry == null || bot == null || bot.getMap() == null) {
            return;
        }
        if (AgentInventorySellTrashService.collectSellTrashEquips(entry, bot).isEmpty()) {
            AgentBotShopRuntime.replyNow(entry, AgentDialogueCatalog.shopNoTrashEquipsReply());
            return;
        }

        AgentBotShopStateRuntime.setShopSellTrashPending(entry, true);
        if (AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return;
        }

        NpcShopMatch match = findBestShop(bot, true);
        if (match == null) {
            AgentBotShopStateRuntime.setShopSellTrashPending(entry, false);
            AgentBotShopRuntime.replyNow(entry, AgentDialogueCatalog.shopNotFoundReply());
            return;
        }

        AgentBotShopRuntime.replyNow(entry, AgentDialogueCatalog.shopSellTrashStartReply());
        startShopVisit(entry, bot, match);
    }

    private static void startShopVisit(BotEntry entry, Character bot, NpcShopMatch match) {
        AgentBotShopStateRuntime.startShopVisit(
                entry,
                match.npcPos,
                pickShopApproachPoint(match.npcPos, entry, bot),
                (int) AgentRandom.randMs(0, SHOP_APPROACH_DELAY_MAX_MS),
                System.currentTimeMillis());
    }

    public static boolean tickShopVisit(AgentRuntimeEntry entry, Character bot) {
        return tickShopVisit(asBotEntry(entry), bot);
    }

    public static boolean tickShopVisit(BotEntry entry, Character bot) {
        if (!AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        if (!AgentBotShopStateRuntime.hasShopNpcPosition(entry)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopLostReply());
            return false;
        }
        long now = System.currentTimeMillis();
        if (AgentBotShopStateRuntime.visitTimedOut(entry, now, SHOP_VISIT_TIMEOUT_MS)) {
            AgentBotShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopReachTimeoutReply());
            clearShopState(entry);
            return false;
        }
        if (AgentBotShopStateRuntime.sequenceTimedOut(entry, now, SHOP_SEQUENCE_TIMEOUT_MS)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopSequenceTimeoutReply());
            return false;
        }
        if (AgentBotShopStateRuntime.shopApproachDelayMs(entry) > 0) {
            AgentBotShopStateRuntime.setShopApproachDelayMs(
                    entry,
                    AgentMovementTimers.tickDown(AgentBotShopStateRuntime.shopApproachDelayMs(entry)));
            return false;
        }

        Point botPos = bot.getPosition();
        Point target = AgentBotShopStateRuntime.shopTargetOrNpcPosition(entry);
        boolean reachedApproach = manhattan(botPos, target) <= SHOP_ARRIVE_DIST;
        boolean stuckAtNpc = !AgentBotShopStateRuntime.shopSequenceActive(entry)
                && !reachedApproach
                && isStuckNearNpc(entry, botPos, now);
        if (reachedApproach || stuckAtNpc) {
            if (!AgentBotShopStateRuntime.shopSequenceActive(entry)) {
                AgentBotShopStateRuntime.markShopSequenceActive(entry, System.currentTimeMillis());
                AgentBotShopRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.shoppingReplies()));
                Point npcPos = AgentBotShopStateRuntime.shopNpcPosition(entry);
                scheduleShopStep(entry, () -> executePurchases(entry, bot, npcPos));
            }
            return true;
        }

        return true;
    }

    private static boolean isStuckNearNpc(BotEntry entry, Point botPos, long now) {
        return AgentBotShopStateRuntime.stuckNearNpc(
                entry, botPos, now, SHOP_STUCK_FALLBACK_MS, SHOP_STUCK_MOVE_TOLERANCE_PX, SHOP_ARRIVE_DIST);
    }

    private static int manhattan(Point a, Point b) {
        return AgentShopApproachPolicy.manhattan(a, b);
    }

    private static NpcShopMatch findBestShop(Character bot, boolean allowAnyShop) {
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
            if (allowAnyShop || shopHasAnythingNeeded(bot, shop)) {
                return new NpcShopMatch(npc, shop, npc.getPosition());
            }
        }
        return null;
    }

    private static boolean shopHasAnythingNeeded(Character bot, Shop shop) {
        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (needsFixedAmmoForShop(bot, shop, wt, ammoTriggerThreshold())) {
            return true;
        }
        if (needsRechargeForShop(bot, wt, ammoTriggerThreshold())) {
            return true;
        }
        int[] pots = AgentPotionService.countPotions(bot);
        if (pots[0] < AgentRuntimeConfig.cfg.POT_LOW_WARN * 5 && findPotionItem(shop, bot, true) != null) {
            return true;
        }
        if (pots[1] < AgentRuntimeConfig.cfg.POT_LOW_WARN * 5 && findPotionItem(shop, bot, false) != null) {
            return true;
        }
        return false;
    }

    private static void executePurchases(BotEntry entry, Character bot, Point npcPos) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopKeeperUnreachableReply());
            return;
        }

        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        List<AgentShopPurchaseAction<BotEntry>> actions = new ArrayList<>();

        if (shouldRechargeWhileShopping(bot, wt)) {
            actions.add((sequence, shop) -> {
                AgentShopBuyReport recharge = doRecharge(bot, shop, wt);
                if (recharge.quantity() > 0) {
                    int recharged = recharge.quantity();
                    String ammoName = wt == WeaponType.GUN ? "bullets" : "throwing stars";
                    sequence.bought().add("refilled " + recharged + " set"
                            + (recharged > 1 ? "s" : "") + " of my " + ammoName);
                }
                return sequence.withFirstShortfall(recharge);
            });
        }
        if (shouldBuyFixedAmmoWhileShopping(bot, wt)) {
            actions.add((sequence, shop) -> appendBuyReport(sequence, buyAmmo(bot, shop, wt), "ammo"));
        }
        actions.add((sequence, shop) -> {
            int[] pots = AgentPotionService.countPotions(bot);
            if (pots[0] < AgentRuntimeConfig.cfg.POT_LOW_WARN * 5) {
                return appendBuyReport(sequence, buyPotions(bot, shop, true), "HP pots");
            }
            return sequence;
        });
        actions.add((sequence, shop) -> {
            int[] pots = AgentPotionService.countPotions(bot);
            if (pots[1] < AgentRuntimeConfig.cfg.POT_LOW_WARN * 5) {
                return appendBuyReport(sequence, buyPotions(bot, shop, false), "MP pots");
            }
            return sequence;
        });

        runPurchaseStep(new AgentShopPurchaseSequence<>(entry, bot, npcPos, actions, new ArrayList<>(), null), 0);
    }

    private static void runPurchaseStep(AgentShopPurchaseSequence<BotEntry> sequence, int index) {
        if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopBuyInterruptedReply());
            return;
        }
        if (index >= sequence.actions().size()) {
            if (AgentBotShopStateRuntime.shopSellTrashPending(sequence.entry())) {
                startSellTrashSequence(sequence);
            } else {
                finishPurchaseSequence(sequence, true);
            }
            return;
        }

        NPC npc = findNpcNear(sequence.bot(), sequence.npcPos());
        if (npc == null) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopKeeperGoneBuyReply());
            return;
        }
        Shop shop = ShopFactory.getInstance().getShopForNPC(npc.getId());
        if (shop == null) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopClosedBuyReply());
            return;
        }

        AgentShopPurchaseSequence<BotEntry> next = sequence.actions().get(index).run(sequence, shop);
        scheduleShopStep(sequence.entry(), () -> runPurchaseStep(next, index + 1));
    }

    private static void finishPurchaseSequence(AgentShopPurchaseSequence<BotEntry> sequence, boolean announceIfEmpty) {
        if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopFinishFailedReply());
            return;
        }

        Runnable finish = () -> {
            if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
                abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopFinishFailedReply());
                return;
            }
            if (sequence.firstShortfall() != null) {
                AgentBotShopRuntime.sayMapNow(sequence.bot(), buildShortfallMessage(sequence.firstShortfall()));
            } else if (announceIfEmpty && sequence.bought().isEmpty()) {
                // Never end a resupply visit silently: nothing was bought and nothing fell short.
                AgentBotShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopEmptyResupplyReply());
            }
            clearShopState(sequence.entry());
        };

        if (!sequence.bought().isEmpty()) {
            AgentBotShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopBoughtReply(sequence.bought()));
            AgentPotionService.setupAutopotForBot(sequence.bot());
            AgentBotCombatAmmoCheckRuntime.tickAmmoCheck(sequence.entry(), sequence.bot(),
                    AgentCombatConfig.cfg.AMMO_LOW_WARN, AgentRuntimeConfig.cfg.POT_LOW_WARN);
            scheduleShopStep(sequence.entry(), finish);
            return;
        }

        finish.run();
    }

    private static void startSellTrashSequence(AgentShopPurchaseSequence<BotEntry> sequence) {
        List<Item> items = AgentInventorySellTrashService.collectSellTrashEquips(sequence.entry(), sequence.bot());
        if (items.isEmpty()) {
            AgentBotShopStateRuntime.setShopSellTrashPending(sequence.entry(), false);
            AgentBotShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopNoTrashEquipsReply());
            finishPurchaseSequence(sequence, false);
            return;
        }

        List<Item> plan = List.copyOf(items);
        scheduleShopStep(sequence.entry(), SELL_TRASH_STEP_DELAY_MS,
                () -> runSellTrashStep(
                        sequence.entry(),
                        sequence.bot(),
                        sequence.npcPos(),
                        0,
                        Collections.newSetFromMap(new IdentityHashMap<>()),
                        plan,
                        sequence.bought(),
                        sequence.firstShortfall()));
    }

    private static void runSellTrashStep(BotEntry entry, Character bot, Point npcPos, int soldCount, Set<Item> failedItems, List<Item> plan,
                                         List<String> bought, AgentShopBuyReport firstShortfall) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopSellInterruptedReply());
            return;
        }

        List<Item> items = plan.stream()
                .filter(item -> AgentInventoryItemPolicy.hasItem(bot, item))
                .filter(item -> !failedItems.contains(item))
                .toList();
        if (items.isEmpty()) {
            AgentBotShopStateRuntime.setShopSellTrashPending(entry, false);
            if (soldCount > 0) {
                AgentBotShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopSoldTrashReply(soldCount));
            }
            if (!failedItems.isEmpty()) {
                AgentBotShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopSellTrashFailureReply(failedItems.size()));
            } else if (soldCount == 0) {
                AgentBotShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopNoTrashEquipsReply());
            }
            finishPurchaseSequence(new AgentShopPurchaseSequence<>(entry, bot, npcPos, List.of(), bought, firstShortfall), false);
            return;
        }

        Item item = items.get(0);
        if (!AgentInventoryItemPolicy.hasItem(bot, item)) {
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runSellTrashStep(entry, bot, npcPos, soldCount, failedItems, plan, bought, firstShortfall));
            return;
        }

        NPC npc = findNpcNear(bot, npcPos);
        if (npc == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopKeeperGoneSellReply());
            return;
        }
        Shop shop = ShopFactory.getInstance().getShopForNPC(npc.getId());
        if (shop == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopClosedSellReply());
            return;
        }

        shop.sell(bot.getClient(), InventoryType.EQUIP, item.getPosition(), (short) 1);
        if (AgentInventoryItemPolicy.hasItem(bot, item)) {
            failedItems.add(item);
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runSellTrashStep(entry, bot, npcPos, soldCount, failedItems, plan, bought, firstShortfall));
            return;
        }

        int nextSoldCount = soldCount + 1;
        scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                () -> runSellTrashStep(entry, bot, npcPos, nextSoldCount, failedItems, plan, bought, firstShortfall));
    }

    private static AgentShopPurchaseSequence<BotEntry> appendBuyReport(
            AgentShopPurchaseSequence<BotEntry> sequence,
            AgentShopBuyReport report,
            String fallbackName) {
        if (report.quantity() > 0) {
            sequence.bought().add(report.quantity() + " " + resolveItemName(report.itemId(), fallbackName));
        }
        return sequence.withFirstShortfall(report);
    }

    private static boolean needsAmmo(Character bot, WeaponType wt) {
        return AgentShopAmmoPolicy.needsFixedAmmoWeapon(wt);
    }

    private static int ammoTriggerThreshold() {
        return AgentShopAmmoPolicy.triggerThreshold(AgentCombatConfig.cfg.AMMO_LOW_WARN, AMMO_TRIGGER_THRESHOLD);
    }

    private static int ammoTargetThreshold() {
        return AgentShopAmmoPolicy.targetThreshold(AgentCombatConfig.cfg.AMMO_LOW_WARN, AMMO_TARGET_THRESHOLD);
    }

    private static boolean needsFixedAmmoForShop(Character bot, Shop shop, WeaponType wt, int threshold) {
        if (!needsAmmo(bot, wt) || AgentCombatAmmoCounter.countAmmo(bot, wt) >= threshold) {
            return false;
        }
        return shop == null || findAmmoItem(shop, wt) != null;
    }

    // True when the bot's BEST rechargeable ammo (highest-attack star/bullet matching the
    // weapon) is below the threshold AND has a partial stack that can actually be refilled.
    // Keying on the best item means a pile of weaker stars can't mask a depleted good stack,
    // and the partial-stack check stops pointless trips/silent no-ops when nothing is refillable.
    private static boolean needsRechargeForShop(Character bot, WeaponType wt, int threshold) {
        return AgentShopAmmoPolicy.needsRecharge(
                bot.getInventory(InventoryType.USE).list(),
                wt,
                threshold,
                projectileWatk,
                itemId -> ammoSlotMax.slotMax(bot, itemId));
    }

    static boolean shouldRechargeWhileShopping(Character bot, WeaponType wt) {
        return needsRechargeForShop(bot, wt, ammoTriggerThreshold());
    }

    static boolean shouldBuyFixedAmmoWhileShopping(Character bot, WeaponType wt) {
        return AgentShopAmmoPolicy.shouldBuyFixedAmmo(wt, AgentCombatAmmoCounter.countAmmo(bot, wt),
                ammoTargetThreshold());
    }

    private static boolean isRechargeWeaponType(WeaponType wt) {
        return AgentShopAmmoPolicy.isRechargeWeaponType(wt);
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

    private static AgentShopBuyReport buyAmmo(Character bot, Shop shop, WeaponType wt) {
        ShopSlotItem ammo = findAmmoItem(shop, wt);
        if (ammo == null) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }

        int target = ammoTargetThreshold();
        int current = AgentCombatAmmoCounter.countAmmo(bot, wt);
        return buyFixedCostItem(bot, shop, ammo, Math.max(0, target - current), 1000);
    }

    private static int bestRechargeAmmoId(Character bot, WeaponType wt) {
        return AgentShopAmmoPolicy.bestRechargeAmmoId(bot.getInventory(InventoryType.USE).list(), wt, projectileWatk);
    }

    private static boolean matchesRechargeWeapon(int itemId, WeaponType wt) {
        return AgentShopAmmoPolicy.matchesRechargeWeapon(itemId, wt);
    }

    private static AgentShopBuyReport doRecharge(Character bot, Shop shop, WeaponType wt) {
        // Only recharge ammo matching the equipped weapon (claw->stars, gun->bullets) and only
        // the best stacks by attack: recharging off-weapon or low-tier leftovers just wastes meso,
        // and an off-weapon failure must never short-circuit the real ammo refill.
        List<Item> refillable = new ArrayList<>();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (!ItemConstants.isRechargeable(id) || !matchesRechargeWeapon(id, wt)) {
                continue;
            }
            if (item.getQuantity() >= ammoSlotMax.slotMax(bot, id)) {
                continue;
            }
            refillable.add(item);
        }
        refillable.sort((a, b) -> Integer.compare(
                projectileWatk.applyAsInt(b.getItemId()), projectileWatk.applyAsInt(a.getItemId())));

        int recharged = 0;
        int attempted = 0;
        int shortfallItemId = 0;
        AgentShopShortfallReason reason = AgentShopShortfallReason.NONE;
        for (Item item : refillable) {
            if (recharged >= RECHARGE_MAX_SETS) {
                break;
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
                case NOT_ENOUGH_MESO -> AgentShopShortfallReason.NO_MESO;
                case NO_SPACE -> AgentShopShortfallReason.NO_SPACE;
                default -> AgentShopShortfallReason.OTHER;
            };
            break;
        }
        return new AgentShopBuyReport(shortfallItemId, recharged, attempted, reason);
    }

    private static ShopSlotItem findPotionItem(Shop shop, Character bot, boolean forHp) {
        int maxStat = forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp();
        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                shop.getItems(),
                maxStat,
                forHp,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentUseItemClassificationPolicy::itemEffect);
        return selected == null ? null : new ShopSlotItem(selected.slot(), selected.shopItem());
    }

    private static AgentShopBuyReport buyPotions(Character bot, Shop shop, boolean forHp) {
        ShopSlotItem pot = findPotionItem(shop, bot, forHp);
        if (pot == null) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }

        int target = AgentRuntimeConfig.cfg.POT_LOW_WARN * POT_TARGET_THRESHOLD;
        int[] pots = AgentPotionService.countPotions(bot);
        int current = forHp ? pots[0] : pots[1];
        return buyFixedCostItem(bot, shop, pot, Math.max(0, target - current), 100);
    }

    private static AgentShopBuyReport buyFixedCostItem(Character bot, Shop shop, ShopSlotItem item, int desiredQuantity, int batchSize) {
        if (item == null || desiredQuantity <= 0) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }

        int totalBought = 0;
        AgentShopShortfallReason reason = AgentShopShortfallReason.NONE;
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
                reason = AgentShopShortfallReason.NO_MESO;
                int affordable = price > 0 ? Math.min(remaining, bot.getMeso() / price) : 0;
                if (affordable > 0) {
                    Shop.TransactionResult partial = shop.buyDirect(bot, item.slot, item.shopItem.getItemId(), (short) affordable);
                    if (partial == Shop.TransactionResult.SUCCESS) {
                        totalBought += affordable;
                    } else if (partial == Shop.TransactionResult.NO_SPACE) {
                        reason = AgentShopShortfallReason.NO_SPACE;
                    }
                }
            } else if (result == Shop.TransactionResult.NO_SPACE) {
                reason = AgentShopShortfallReason.NO_SPACE;
            } else {
                reason = AgentShopShortfallReason.OTHER;
            }
            break;
        }

        return new AgentShopBuyReport(item.shopItem.getItemId(), totalBought, desiredQuantity, reason);
    }

    private static String buildShortfallMessage(AgentShopBuyReport report) {
        String itemName = resolveItemName(report.itemId(), "item");
        String got = GameConstants.numberWithCommas(report.quantity());
        String want = GameConstants.numberWithCommas(report.requestedQuantity());
        return switch (report.reason()) {
            case NO_SPACE -> AgentDialogueCatalog.shopNoSpaceReply(itemName, got, want, report.quantity());
            case OTHER -> AgentDialogueCatalog.shopRefusedReply(itemName);
            case NO_MESO, NONE -> AgentDialogueCatalog.shopNoMesoReply(itemName, got, want, report.quantity());
        };
    }

    private static String resolveItemName(int itemId, String fallbackName) {
        String name = ItemInformationProvider.getInstance().getName(itemId);
        return name != null ? name : fallbackName;
    }

    private static boolean isShopSequenceValid(BotEntry entry, Character bot, Point npcPos) {
        if (bot.getMap() == null) {
            return false;
        }
        // Accept proximity to the approach point OR the NPC itself: the sequence can start
        // via the stuck-at-NPC fallback, where the bot is near the NPC but not the approach point.
        return AgentBotShopStateRuntime.sequenceValid(entry, bot.getPosition(), npcPos, SHOP_ARRIVE_DIST)
                && findNpcNear(bot, npcPos) != null;
    }

    public static void cancelShopVisit(BotEntry entry) {
        clearShopState(entry);
    }

    // Abort the shop visit and tell the owner why. Player commands cancel via
    // cancelShopVisit (which clears shopVisitPending) and scheduleShopStep guards on
    // that flag, so a cleared flag here means a concurrent player cancel — stay silent.
    private static void abortShop(BotEntry entry, Character bot, String reason) {
        if (AgentBotShopStateRuntime.shopVisitPending(entry)) {
            AgentBotShopRuntime.sayMapNow(bot, reason);
        }
        clearShopState(entry);
    }

    private static void clearShopState(BotEntry entry) {
        AgentBotShopStateRuntime.clearShopState(entry);
    }

    private static long stepDelayMs() {
        return AgentBotShopRuntime.randomDelayMs(SHOP_STEP_DELAY_MIN_MS, SHOP_STEP_DELAY_MAX_MS);
    }

    private static void scheduleShopStep(BotEntry entry, Runnable step) {
        scheduleShopStep(entry, stepDelayMs(), step);
    }

    private static void scheduleShopStep(BotEntry entry, long delayMs, Runnable step) {
        AgentBotShopRuntime.afterDelay(delayMs, () -> {
            if (!AgentBotShopStateRuntime.shouldRunScheduledShopStep(entry)) {
                return;
            }
            try {
                step.run();
            } catch (RuntimeException exception) {
                abortShop(entry, AgentBotRuntimeIdentityRuntime.bot(entry), AgentDialogueCatalog.shopScheduleErrorReply());
                throw exception;
            }
        });
    }

    private static Point pickShopApproachPoint(Point npcPos, BotEntry entry, Character bot) {
        var footholds = bot.getMap().getFootholds();
        if (footholds == null) {
            return npcPos;
        }
        List<Point> candidates = AgentShopApproachPolicy.footholdCandidatesNear(
                npcPos, footholds.getAllFootholds(), SHOP_MANHATTAN_RADIUS);
        if (candidates.isEmpty()) {
            return npcPos;
        }
        AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfileOrCharacter(entry, bot);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(bot.getMap(), profile);
        if (graph != null) {
            Point botPos = bot.getPosition();
            int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            if (startRegionId >= 0) {
                List<Point> reachable = new ArrayList<>();
                for (Point candidate : candidates) {
                    int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                            graph, entry, bot.getMap(), candidate);
                    if (targetRegionId < 0) continue;
                    if (startRegionId == targetRegionId
                            || !AgentNavigationPathService.findPath(graph, bot.getMap(), botPos,
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
