package server.agents.capabilities.shop;

import server.agents.capabilities.movement.AgentMovementTimers;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.shop.AgentShopAmmoPolicy;
import server.agents.capabilities.shop.AgentShopApproachPolicy;
import server.agents.capabilities.shop.AgentShopPotionPolicy;
import server.agents.capabilities.supplies.AgentPotionInventoryPolicy;
import server.agents.capabilities.supplies.AgentPotionRecoveryPolicy;
import server.agents.capabilities.supplies.AgentPotionService;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.inventory.demand.AgentEtcSaleCandidate;
import server.agents.capabilities.inventory.demand.AgentQuestItemDispositionRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentShopGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.capabilities.shop.AgentShopRuntime;
import server.agents.capabilities.supplies.AgentCombatAmmoCheckRuntime;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.Shop;
import server.ShopItem;
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

public final class AgentShopService {

    private static final int SHOP_MANHATTAN_RADIUS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_MANHATTAN_RADIUS");
    private static final int SHOP_ARRIVE_DIST = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_ARRIVE_DIST");
    private static final int SHOP_NPC_SEARCH_DIST = config.AgentTuning.intValue(
            "server.agents.capabilities.shop.AgentShopService.SHOP_NPC_SEARCH_DIST");
    private static final int SHOP_APPROACH_DELAY_MAX_MS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_APPROACH_DELAY_MAX_MS");
    private static final int SHOP_STEP_DELAY_MIN_MS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_STEP_DELAY_MIN_MS");
    private static final int SHOP_STEP_DELAY_MAX_MS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_STEP_DELAY_MAX_MS");
    private static final int SELL_TRASH_STEP_DELAY_MS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SELL_TRASH_STEP_DELAY_MS");
    private static final long SHOP_VISIT_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.capabilities.shop.AgentShopService.SHOP_VISIT_TIMEOUT_MS");
    private static final long SHOP_SEQUENCE_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.capabilities.shop.AgentShopService.SHOP_SEQUENCE_TIMEOUT_MS");
    private static final long SHOP_STUCK_FALLBACK_MS = config.AgentTuning.longValue("server.agents.capabilities.shop.AgentShopService.SHOP_STUCK_FALLBACK_MS");
    private static final int SHOP_STUCK_MOVE_TOLERANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.SHOP_STUCK_MOVE_TOLERANCE_PX");
    private static final int AMMO_TRIGGER_THRESHOLD = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.AMMO_TRIGGER_THRESHOLD");
    private static final int AMMO_TARGET_THRESHOLD = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.AMMO_TARGET_THRESHOLD"); // full target when buying at shop
    private static final int RECHARGE_MAX_SETS = config.AgentTuning.intValue("server.agents.capabilities.shop.AgentShopService.RECHARGE_MAX_SETS"); // cap recharge to the best N own-type stacks

    private AgentShopService() {}

    private record NpcShopMatch(NPC npc, Shop shop, Point npcPos) {}

    private record ShopSlotItem(
            short slot,
            ShopItem shopItem,
            AgentPotionRecoveryPolicy.Recovery recovery) {}

    public static void onMapChange(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        clearShopState(entry);
        if (bot == null || bot.getMap() == null) {
            return;
        }

        NpcShopMatch match = findBestShop(bot, false, inventory);
        if (match == null) {
            return;
        }

        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean needsRecharge = needsRechargeForShop(bot, wt, ammoTriggerThreshold(), inventory);
        boolean needsAmmoForShop = needsFixedAmmoForShop(bot, match.shop, wt, ammoTriggerThreshold());
        boolean needsHpPots = needsPotionStock(bot, match.shop, true);
        boolean needsMpPots = needsPotionStock(bot, match.shop, false);
        if (!needsRecharge && !needsAmmoForShop && !needsHpPots && !needsMpPots) {
            return;
        }

        long distSq = (long) bot.getPosition().distanceSq(match.npcPos);
        if (distSq > 1000L * 1000L) {
            AgentShopRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.shopResupplyReplies()));
        }

        startShopVisit(entry, bot, match);
    }

    /** Starts the planned MVP shop visit even when current supplies already satisfy restock thresholds. */
    public static boolean requestVisitAtNpc(AgentRuntimeEntry entry, Character bot, int npcId) {
        return requestVisitAtNpc(entry, bot, npcId, 0);
    }

    /**
     * Starts a planned shop visit while preserving the supplied meso floor for later journey costs.
     */
    public static boolean requestVisitAtNpc(
            AgentRuntimeEntry entry, Character bot, int npcId, int minimumMesoReserve) {
        return requestVisitAtNpc(entry, bot, npcId, minimumMesoReserve, 0, 0);
    }

    public static boolean requestVisitAtNpc(
            AgentRuntimeEntry entry,
            Character bot,
            int npcId,
            int minimumMesoReserve,
            int requiredItemId,
            int requiredItemCount) {
        if (entry == null || bot == null || bot.getMap() == null) {
            return false;
        }
        NpcShopMatch match = findShopForNpc(bot, npcId);
        if (match == null) {
            return false;
        }
        startShopVisit(entry, bot, match, minimumMesoReserve, requiredItemId, requiredItemCount);
        return true;
    }

    public static void requestSellTrashVisit(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        if (entry == null || bot == null || bot.getMap() == null) {
            return;
        }
        if (AgentInventorySellTrashService.collectSellTrashEquips(entry, bot, inventory).isEmpty()) {
            AgentShopRuntime.replyNow(entry, AgentDialogueCatalog.shopNoTrashEquipsReply());
            return;
        }

        AgentShopStateRuntime.setShopSellTrashPending(entry, true);
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            return;
        }

        NpcShopMatch match = findBestShop(bot, true, inventory);
        if (match == null) {
            AgentShopStateRuntime.setShopSellTrashPending(entry, false);
            AgentShopRuntime.replyNow(entry, AgentDialogueCatalog.shopNotFoundReply());
            return;
        }

        AgentShopRuntime.replyNow(entry, AgentDialogueCatalog.shopSellTrashStartReply());
        startShopVisit(entry, bot, match);
    }

    private static void startShopVisit(AgentRuntimeEntry entry, Character bot, NpcShopMatch match) {
        startShopVisit(entry, bot, match, 0);
    }

    private static void startShopVisit(
            AgentRuntimeEntry entry, Character bot, NpcShopMatch match, int minimumMesoReserve) {
        startShopVisit(entry, bot, match, minimumMesoReserve, 0, 0);
    }

    private static void startShopVisit(
            AgentRuntimeEntry entry,
            Character bot,
            NpcShopMatch match,
            int minimumMesoReserve,
            int requiredItemId,
            int requiredItemCount) {
        AgentShopStateRuntime.startShopVisit(
                entry,
                match.npcPos,
                pickShopApproachPoint(match.npcPos, entry, bot),
                (int) AgentRandom.randMs(0, SHOP_APPROACH_DELAY_MAX_MS),
                minimumMesoReserve,
                requiredItemId,
                requiredItemCount,
                System.currentTimeMillis());
    }

    public static boolean tickShopVisit(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        if (!AgentShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        if (!AgentShopStateRuntime.hasShopNpcPosition(entry)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopLostReply());
            return false;
        }
        long now = System.currentTimeMillis();
        if (AgentShopStateRuntime.visitTimedOut(entry, now, SHOP_VISIT_TIMEOUT_MS)) {
            AgentShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopReachTimeoutReply());
            clearShopState(entry);
            return false;
        }
        if (AgentShopStateRuntime.sequenceTimedOut(entry, now, SHOP_SEQUENCE_TIMEOUT_MS)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopSequenceTimeoutReply());
            return false;
        }
        if (AgentShopStateRuntime.shopApproachDelayMs(entry) > 0) {
            AgentShopStateRuntime.setShopApproachDelayMs(
                    entry,
                    AgentMovementTimers.tickDown(AgentShopStateRuntime.shopApproachDelayMs(entry)));
            return false;
        }

        Point botPos = bot.getPosition();
        Point target = AgentShopStateRuntime.shopTargetOrNpcPosition(entry);
        boolean reachedApproach = manhattan(botPos, target) <= SHOP_ARRIVE_DIST;
        boolean stuckAtNpc = !AgentShopStateRuntime.shopSequenceActive(entry)
                && !reachedApproach
                && isStuckNearNpc(entry, botPos, now);
        if (reachedApproach || stuckAtNpc) {
            if (!AgentShopStateRuntime.shopSequenceActive(entry)) {
                AgentShopStateRuntime.markShopSequenceActive(entry, System.currentTimeMillis());
                AgentShopRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.shoppingReplies()));
                Point npcPos = AgentShopStateRuntime.shopNpcPosition(entry);
                scheduleShopStep(entry, () -> executePurchases(entry, bot, inventory, npcPos));
            }
            return true;
        }

        return true;
    }

    private static boolean isStuckNearNpc(AgentRuntimeEntry entry, Point botPos, long now) {
        return AgentShopStateRuntime.stuckNearNpc(
                entry, botPos, now, SHOP_STUCK_FALLBACK_MS, SHOP_STUCK_MOVE_TOLERANCE_PX, SHOP_ARRIVE_DIST);
    }

    private static int manhattan(Point a, Point b) {
        return AgentShopApproachPolicy.manhattan(a, b);
    }

    private static NpcShopMatch findBestShop(Character bot, boolean allowAnyShop, InventoryGateway inventory) {
        List<MapObject> objects = bot.getMap().getMapObjectsInRange(
                new Point(0, 0), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.NPC));

        for (MapObject obj : objects) {
            NPC npc = (NPC) obj;
            if (!npc.hasShop()) {
                continue;
            }
            Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npc.getId());
            if (shop == null) {
                continue;
            }
            if (allowAnyShop || shopHasAnythingNeeded(bot, shop, inventory)) {
                return new NpcShopMatch(npc, shop, npc.getPosition());
            }
        }
        return null;
    }

    private static NpcShopMatch findShopForNpc(Character bot, int npcId) {
        for (MapObject obj : bot.getMap().getMapObjectsInRange(
                new Point(0, 0), Double.POSITIVE_INFINITY, List.of(MapObjectType.NPC))) {
            NPC npc = (NPC) obj;
            if (npc.getId() != npcId || !npc.hasShop()) {
                continue;
            }
            Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npc.getId());
            if (shop != null) {
                return new NpcShopMatch(npc, shop, npc.getPosition());
            }
        }
        return null;
    }

    private static boolean shopHasAnythingNeeded(Character bot, Shop shop, InventoryGateway inventory) {
        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (needsFixedAmmoForShop(bot, shop, wt, ammoTriggerThreshold())) {
            return true;
        }
        if (needsRechargeForShop(bot, wt, ammoTriggerThreshold(), inventory)) {
            return true;
        }
        if (needsPotionStock(bot, shop, true)) {
            return true;
        }
        if (needsPotionStock(bot, shop, false)) {
            return true;
        }
        return false;
    }

    private static void executePurchases(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory, Point npcPos) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopKeeperUnreachableReply());
            return;
        }

        WeaponType wt = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        int minimumMesoReserve = AgentShopStateRuntime.minimumMesoReserve(entry);
        List<AgentShopPurchaseAction<AgentRuntimeEntry>> actions = new ArrayList<>();

        int requiredItemId = AgentShopStateRuntime.requiredItemId(entry);
        int requiredItemCount = AgentShopStateRuntime.requiredItemCount(entry);
        boolean itemOnlyVisit = requiredItemId > 0 && requiredItemCount > 0;
        if (itemOnlyVisit) {
            actions.add((sequence, shop) -> {
                int missing = Math.max(0, requiredItemCount
                        - bot.getInventory(ItemConstants.getInventoryType(requiredItemId))
                        .countById(requiredItemId));
                if (missing > 0) {
                    AgentShopRuntime.sayMapNow(
                            bot,
                            AgentDialogueCatalog.shopPurchaseIntent(
                                    missing,
                                    resolveItemName(requiredItemId, "travel item", inventory),
                                    null));
                }
                return appendBuyReport(sequence,
                        buyFixedCostItem(bot, shop, findShopItem(shop, requiredItemId),
                                missing, Math.max(1, missing), 0),
                        "required travel item");
            });
        }

        if (!itemOnlyVisit
                && minimumMesoReserve == 0
                && shouldRechargeWhileShopping(bot, wt, inventory)) {
            actions.add((sequence, shop) -> {
                AgentShopBuyReport recharge = doRecharge(bot, shop, wt, sequence.inventory());
                if (recharge.quantity() > 0) {
                    int recharged = recharge.quantity();
                    String ammoName = wt == WeaponType.GUN ? "bullets" : "throwing stars";
                    sequence.bought().add("refilled " + recharged + " set"
                            + (recharged > 1 ? "s" : "") + " of my " + ammoName);
                }
                return sequence.withFirstShortfall(recharge);
            });
        }
        if (!itemOnlyVisit && shouldBuyFixedAmmoWhileShopping(bot, wt)) {
            actions.add((sequence, shop) -> appendBuyReport(
                    sequence, buyAmmo(bot, shop, wt, minimumMesoReserve), "ammo"));
        }
        if (!itemOnlyVisit) {
            actions.add((sequence, shop) ->
                    buyPotionStock(sequence, shop, minimumMesoReserve));
        }

        runPurchaseStep(new AgentShopPurchaseSequence<>(entry, bot, inventory, npcPos, actions, new ArrayList<>(), null), 0);
    }

    private static void runPurchaseStep(AgentShopPurchaseSequence<AgentRuntimeEntry> sequence, int index) {
        if (!isShopSequenceValid(sequence.entry(), sequence.bot(), sequence.npcPos())) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopBuyInterruptedReply());
            return;
        }
        if (index >= sequence.actions().size()) {
            if (AgentShopStateRuntime.shopSellTrashPending(sequence.entry())) {
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
        Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npc.getId());
        if (shop == null) {
            abortShop(sequence.entry(), sequence.bot(), AgentDialogueCatalog.shopClosedBuyReply());
            return;
        }

        AgentShopPurchaseSequence<AgentRuntimeEntry> next = sequence.actions().get(index).run(sequence, shop);
        scheduleShopStep(sequence.entry(), () -> runPurchaseStep(next, index + 1));
    }

    private static void finishPurchaseSequence(AgentShopPurchaseSequence<AgentRuntimeEntry> sequence, boolean announceIfEmpty) {
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
                AgentShopRuntime.sayMapNow(sequence.bot(), buildShortfallMessage(sequence.firstShortfall(), sequence.inventory()));
            } else if (announceIfEmpty && sequence.bought().isEmpty()) {
                // Never end a resupply visit silently: nothing was bought and nothing fell short.
                AgentShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopEmptyResupplyReply());
            }
            AgentShopStateRuntime.completeWorkflow(sequence.entry(), "shop transaction reconciled",
                    System.currentTimeMillis());
            clearShopState(sequence.entry());
        };

        if (!sequence.bought().isEmpty()) {
            AgentShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopBoughtReply(sequence.bought()));
            AgentPotionService.setupAutopotForBot(sequence.bot());
            AgentCombatAmmoCheckRuntime.tickAmmoCheck(sequence.entry(), sequence.bot(),
                    AgentCombatConfig.cfg.AMMO_LOW_WARN, AgentRuntimeConfig.cfg.POT_LOW_WARN);
            scheduleShopStep(sequence.entry(), finish);
            return;
        }

        finish.run();
    }

    private static void startSellTrashSequence(AgentShopPurchaseSequence<AgentRuntimeEntry> sequence) {
        List<Item> items = AgentInventorySellTrashService.collectSellTrashEquips(
                sequence.entry(), sequence.bot(), sequence.inventory());
        List<AgentEtcSaleCandidate> etcPlan =
                AgentQuestItemDispositionRuntime.collectShopSaleCandidates(
                        sequence.entry(), sequence.bot(), sequence.inventory(),
                        System.currentTimeMillis());
        if (items.isEmpty() && etcPlan.isEmpty()) {
            AgentShopStateRuntime.setShopSellTrashPending(sequence.entry(), false);
            AgentShopRuntime.sayMapNow(sequence.bot(), AgentDialogueCatalog.shopNoTrashEquipsReply());
            finishPurchaseSequence(sequence, false);
            return;
        }

        if (items.isEmpty()) {
            scheduleShopStep(sequence.entry(), SELL_TRASH_STEP_DELAY_MS,
                    () -> runEtcSaleStep(
                            sequence.entry(), sequence.bot(), sequence.npcPos(), 0, 0, 0,
                            etcPlan, sequence.bought(), sequence.firstShortfall(),
                            sequence.inventory()));
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
                        etcPlan,
                        sequence.bought(),
                        sequence.firstShortfall(),
                        sequence.inventory()));
    }

    private static void runSellTrashStep(AgentRuntimeEntry entry, Character bot, Point npcPos, int soldCount, Set<Item> failedItems, List<Item> plan,
                                         List<AgentEtcSaleCandidate> etcPlan,
                                         List<String> bought, AgentShopBuyReport firstShortfall, InventoryGateway inventory) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopSellInterruptedReply());
            return;
        }

        List<Item> items = plan.stream()
                .filter(item -> AgentInventoryItemPolicy.hasItem(bot, item))
                .filter(item -> !failedItems.contains(item))
                .filter(item -> AgentInventoryReservationRuntime.mayConsume(
                        entry, item, System.currentTimeMillis()))
                .toList();
        if (items.isEmpty()) {
            if (!etcPlan.isEmpty()) {
                scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                        () -> runEtcSaleStep(entry, bot, npcPos, soldCount,
                                failedItems.size(), 0, etcPlan, bought, firstShortfall,
                                inventory));
                return;
            }
            finishSellTrashSequence(entry, bot, inventory, npcPos, soldCount,
                    failedItems.size(), bought, firstShortfall);
            return;
        }

        Item item = items.get(0);
        if (!AgentInventoryReservationRuntime.mayConsume(entry, item, System.currentTimeMillis())) {
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runSellTrashStep(entry, bot, npcPos, soldCount, failedItems,
                            plan, etcPlan, bought, firstShortfall, inventory));
            return;
        }
        if (!AgentInventoryItemPolicy.hasItem(bot, item)) {
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runSellTrashStep(entry, bot, npcPos, soldCount, failedItems,
                            plan, etcPlan, bought, firstShortfall, inventory));
            return;
        }

        NPC npc = findNpcNear(bot, npcPos);
        if (npc == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopKeeperGoneSellReply());
            return;
        }
        Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npc.getId());
        if (shop == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopClosedSellReply());
            return;
        }

        AgentShopGatewayRuntime.shop().sell(bot, shop, InventoryType.EQUIP, item.getPosition(), (short) 1);
        if (AgentInventoryItemPolicy.hasItem(bot, item)) {
            failedItems.add(item);
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runSellTrashStep(entry, bot, npcPos, soldCount, failedItems,
                            plan, etcPlan, bought, firstShortfall, inventory));
            return;
        }

        int nextSoldCount = soldCount + 1;
        scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                () -> runSellTrashStep(entry, bot, npcPos, nextSoldCount, failedItems,
                        plan, etcPlan, bought, firstShortfall, inventory));
    }

    private static void runEtcSaleStep(
            AgentRuntimeEntry entry,
            Character bot,
            Point npcPos,
            int soldCount,
            int failedCount,
            int index,
            List<AgentEtcSaleCandidate> plan,
            List<String> bought,
            AgentShopBuyReport firstShortfall,
            InventoryGateway inventory) {
        if (!isShopSequenceValid(entry, bot, npcPos)) {
            abortShop(entry, bot, AgentDialogueCatalog.shopSellInterruptedReply());
            return;
        }
        if (index >= plan.size()) {
            finishSellTrashSequence(entry, bot, inventory, npcPos, soldCount,
                    failedCount, bought, firstShortfall);
            return;
        }

        AgentEtcSaleCandidate candidate = plan.get(index);
        Item item = candidate.item();
        int before = bot.getInventory(InventoryType.ETC).countById(item.getItemId());
        int reserved = AgentInventoryReservationRuntime.ledger(entry)
                .reservedQuantity(item.getItemId(), System.currentTimeMillis());
        int available = Math.max(0, before - reserved);
        int quantity = Math.min(candidate.quantity(),
                Math.min(item.getQuantity(), available));
        if (!AgentInventoryItemPolicy.hasItem(bot, item) || quantity <= 0) {
            scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                    () -> runEtcSaleStep(entry, bot, npcPos, soldCount, failedCount,
                            index + 1, plan, bought, firstShortfall, inventory));
            return;
        }

        NPC npc = findNpcNear(bot, npcPos);
        if (npc == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopKeeperGoneSellReply());
            return;
        }
        Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npc.getId());
        if (shop == null) {
            abortShop(entry, bot, AgentDialogueCatalog.shopClosedSellReply());
            return;
        }

        AgentShopGatewayRuntime.shop().sell(
                bot, shop, InventoryType.ETC, item.getPosition(), (short) quantity);
        int after = bot.getInventory(InventoryType.ETC).countById(item.getItemId());
        int sold = Math.max(0, before - after);
        scheduleShopStep(entry, SELL_TRASH_STEP_DELAY_MS,
                () -> runEtcSaleStep(entry, bot, npcPos, soldCount + sold,
                        sold > 0 ? failedCount : failedCount + 1,
                        index + 1, plan, bought, firstShortfall, inventory));
    }

    private static void finishSellTrashSequence(
            AgentRuntimeEntry entry,
            Character bot,
            InventoryGateway inventory,
            Point npcPos,
            int soldCount,
            int failedCount,
            List<String> bought,
            AgentShopBuyReport firstShortfall) {
        AgentShopStateRuntime.setShopSellTrashPending(entry, false);
        if (soldCount > 0) {
            AgentShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopSoldTrashReply(soldCount));
        }
        if (failedCount > 0) {
            AgentShopRuntime.sayMapNow(bot,
                    AgentDialogueCatalog.shopSellTrashFailureReply(failedCount));
        } else if (soldCount == 0) {
            AgentShopRuntime.sayMapNow(bot, AgentDialogueCatalog.shopNoTrashEquipsReply());
        }
        finishPurchaseSequence(new AgentShopPurchaseSequence<>(
                entry, bot, inventory, npcPos, List.of(), bought, firstShortfall), false);
    }

    private static AgentShopPurchaseSequence<AgentRuntimeEntry> appendBuyReport(
            AgentShopPurchaseSequence<AgentRuntimeEntry> sequence,
            AgentShopBuyReport report,
            String fallbackName) {
        if (report.quantity() > 0) {
            sequence.bought().add(report.quantity() + " " + resolveItemName(report.itemId(), fallbackName, sequence.inventory()));
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
    private static boolean needsRechargeForShop(Character bot, WeaponType wt, int threshold, InventoryGateway inventory) {
        return AgentShopAmmoPolicy.needsRecharge(
                bot.getInventory(InventoryType.USE).list(),
                wt,
                threshold,
                inventory::getProjectileWeaponAttack,
                itemId -> inventory.getSlotMax(bot, itemId));
    }

    static boolean shouldRechargeWhileShopping(Character bot, WeaponType wt, InventoryGateway inventory) {
        return needsRechargeForShop(bot, wt, ammoTriggerThreshold(), inventory);
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
                best = new ShopSlotItem((short) i, si, null);
            }
        }
        return best;
    }

    private static AgentShopBuyReport buyAmmo(
            Character bot, Shop shop, WeaponType wt, int minimumMesoReserve) {
        ShopSlotItem ammo = findAmmoItem(shop, wt);
        if (ammo == null) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }

        int target = ammoTargetThreshold();
        int current = AgentCombatAmmoCounter.countAmmo(bot, wt);
        return buyFixedCostItem(
                bot, shop, ammo, Math.max(0, target - current), 1000, minimumMesoReserve);
    }

    private static boolean matchesRechargeWeapon(int itemId, WeaponType wt) {
        return AgentShopAmmoPolicy.matchesRechargeWeapon(itemId, wt);
    }

    private static AgentShopBuyReport doRecharge(Character bot, Shop shop, WeaponType wt, InventoryGateway inventory) {
        // Only recharge ammo matching the equipped weapon (claw->stars, gun->bullets) and only
        // the best stacks by attack: recharging off-weapon or low-tier leftovers just wastes meso,
        // and an off-weapon failure must never short-circuit the real ammo refill.
        List<Item> refillable = new ArrayList<>();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (!ItemConstants.isRechargeable(id) || !matchesRechargeWeapon(id, wt)) {
                continue;
            }
            if (item.getQuantity() >= inventory.getSlotMax(bot, id)) {
                continue;
            }
            refillable.add(item);
        }
        refillable.sort((a, b) -> Integer.compare(
                inventory.getProjectileWeaponAttack(b.getItemId()), inventory.getProjectileWeaponAttack(a.getItemId())));

        int recharged = 0;
        int attempted = 0;
        int shortfallItemId = 0;
        AgentShopShortfallReason reason = AgentShopShortfallReason.NONE;
        for (Item item : refillable) {
            if (recharged >= RECHARGE_MAX_SETS) {
                break;
            }
            Shop.TransactionResult result = AgentShopGatewayRuntime.shop().recharge(bot, shop, item.getPosition());
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

    private static boolean needsPotionStock(Character bot, Shop shop, boolean forHp) {
        ShopSlotItem potion = findPotionItem(shop, bot, forHp);
        if (potion == null) {
            return false;
        }
        return AgentPotionPurchasePolicy.belowReserve(
                potionRecoveryCapacity(bot, forHp),
                forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp(),
                AgentPotionPurchasePolicy.triggerReserveBars(forHp));
    }

    private static AgentShopPurchaseSequence<AgentRuntimeEntry> buyPotionStock(
            AgentShopPurchaseSequence<AgentRuntimeEntry> sequence,
            Shop shop,
            int minimumMesoReserve) {
        Character bot = sequence.bot();
        ShopSlotItem hpPotion = findPotionItem(shop, bot, true);
        ShopSlotItem mpPotion = findPotionItem(shop, bot, false);
        boolean needsHp = needsPotionTarget(bot, hpPotion, true);
        boolean needsMp = needsPotionTarget(bot, mpPotion, false);

        if (needsHp) {
            String intent = potionPurchaseIntent(bot, hpPotion, true, sequence.inventory());
            if (intent != null) {
                AgentShopRuntime.sayMapNow(bot, intent);
            }
        }
        if (needsMp) {
            String intent = potionPurchaseIntent(bot, mpPotion, false, sequence.inventory());
            if (intent != null) {
                AgentShopRuntime.sayMapNow(bot, intent);
            }
        }

        int hpBought = 0;
        int mpBought = 0;
        AgentShopPurchaseSequence<AgentRuntimeEntry> current = sequence;

        AgentShopBuyReport report = buyPotionToReserve(
                bot,
                shop,
                hpPotion,
                true,
                AgentPotionPurchasePolicy.criticalReserveBars(true),
                hpBought,
                false,
                minimumMesoReserve,
                Integer.MAX_VALUE);
        hpBought += report.quantity();
        current = appendBuyReport(current, report, "critical HP pots");

        report = buyPotionToReserve(
                bot,
                shop,
                mpPotion,
                false,
                AgentPotionPurchasePolicy.criticalReserveBars(false),
                mpBought,
                false,
                minimumMesoReserve,
                Integer.MAX_VALUE);
        mpBought += report.quantity();
        current = appendBuyReport(current, report, "critical MP pots");

        boolean mpNeedsNormalStock = needsPotionTarget(bot, mpPotion, false);
        int spendable = Math.max(0, bot.getMeso() - Math.max(0, minimumMesoReserve));
        int hpBudget = AgentPotionPurchasePolicy.normalSpendBudget(
                spendable, mpNeedsNormalStock);
        report = buyPotionToReserve(
                bot,
                shop,
                hpPotion,
                true,
                AgentPotionPurchasePolicy.targetReserveBars(true),
                hpBought,
                true,
                minimumMesoReserve,
                hpBudget);
        hpBought += report.quantity();
        current = appendBuyReport(current, report, "HP pots");

        report = buyPotionToReserve(
                bot,
                shop,
                mpPotion,
                false,
                AgentPotionPurchasePolicy.targetReserveBars(false),
                mpBought,
                true,
                minimumMesoReserve,
                Integer.MAX_VALUE);
        return appendBuyReport(current, report, "MP pots");
    }

    private static AgentShopBuyReport buyPotionToReserve(
            Character bot,
            Shop shop,
            ShopSlotItem potion,
            boolean forHp,
            double reserveBars,
            int alreadyBoughtThisVisit,
            boolean applyMinimumPurchase,
            int minimumMesoReserve,
            int maximumSpend) {
        if (potion == null || potion.recovery == null) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }
        int[] counts = AgentPotionService.countPotions(bot);
        int carriedQuantity = forHp ? counts[0] : counts[1];
        int quantity = AgentPotionPurchasePolicy.quantityToTarget(
                potionRecoveryCapacity(bot, forHp),
                carriedQuantity,
                potion.recovery.primary(),
                forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp(),
                reserveBars,
                alreadyBoughtThisVisit,
                applyMinimumPurchase);
        return buyFixedCostItem(
                bot,
                shop,
                potion,
                quantity,
                100,
                minimumMesoReserve,
                maximumSpend);
    }

    private static boolean needsPotionTarget(
            Character bot, ShopSlotItem potion, boolean forHp) {
        return potion != null
                && AgentPotionPurchasePolicy.belowReserve(
                        potionRecoveryCapacity(bot, forHp),
                        forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp(),
                        AgentPotionPurchasePolicy.targetReserveBars(forHp));
    }

    private static long potionRecoveryCapacity(Character bot, boolean forHp) {
        return AgentPotionInventoryPolicy.recoveryCapacity(
                bot.getInventory(InventoryType.USE).list(),
                AgentUseItemClassificationPolicy::itemEffect,
                bot.getCurrentMaxHp(),
                bot.getCurrentMaxMp(),
                forHp);
    }

    private static String potionPurchaseIntent(
            Character bot,
            ShopSlotItem potion,
            boolean forHp,
            InventoryGateway inventory) {
        int[] counts = AgentPotionService.countPotions(bot);
        int plannedQuantity = AgentPotionPurchasePolicy.quantityToTarget(
                potionRecoveryCapacity(bot, forHp),
                forHp ? counts[0] : counts[1],
                potion.recovery.primary(),
                forHp ? bot.getCurrentMaxHp() : bot.getCurrentMaxMp(),
                AgentPotionPurchasePolicy.targetReserveBars(forHp),
                0,
                true);
        return plannedQuantity <= 0
                ? null
                : AgentDialogueCatalog.shopPurchaseIntent(
                plannedQuantity,
                resolveItemName(potion.shopItem.getItemId(), "potions", inventory),
                forHp ? "HP" : "MP");
    }

    private static ShopSlotItem findPotionItem(Shop shop, Character bot, boolean forHp) {
        int maxHp = bot.getCurrentMaxHp();
        int maxMp = bot.getCurrentMaxMp();
        float threshold = forHp
                ? AgentRuntimeConfig.cfg.AUTOPOT_HP_THRESH
                : AgentRuntimeConfig.cfg.AUTOPOT_MP_THRESH;
        int targetDeficit = Math.max(
                1,
                Math.round((forHp ? maxHp : maxMp) * (1.0f - threshold)));
        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                shop.getItems(),
                maxHp,
                maxMp,
                targetDeficit,
                forHp,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentUseItemClassificationPolicy::itemEffect);
        return selected == null
                ? null
                : new ShopSlotItem(selected.slot(), selected.shopItem(), selected.recovery());
    }

    private static ShopSlotItem findShopItem(Shop shop, int itemId) {
        List<ShopItem> items = shop.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItemId() == itemId && items.get(i).getPrice() > 0) {
                return new ShopSlotItem((short) i, items.get(i), null);
            }
        }
        return null;
    }

    private static AgentShopBuyReport buyFixedCostItem(
            Character bot,
            Shop shop,
            ShopSlotItem item,
            int desiredQuantity,
            int batchSize,
            int minimumMesoReserve) {
        return buyFixedCostItem(
                bot,
                shop,
                item,
                desiredQuantity,
                batchSize,
                minimumMesoReserve,
                Integer.MAX_VALUE);
    }

    private static AgentShopBuyReport buyFixedCostItem(
            Character bot,
            Shop shop,
            ShopSlotItem item,
            int desiredQuantity,
            int batchSize,
            int minimumMesoReserve,
            int maximumSpend) {
        if (item == null || desiredQuantity <= 0) {
            return new AgentShopBuyReport(0, 0, 0, AgentShopShortfallReason.NONE);
        }

        int totalBought = 0;
        AgentShopShortfallReason reason = AgentShopShortfallReason.NONE;
        int price = item.shopItem.getPrice();

        while (totalBought < desiredQuantity) {
            int remaining = desiredQuantity - totalBought;
            long spent = (long) totalBought * price;
            int remainingBudget = maximumSpend == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) maximumSpend - spent));
            int affordable = affordableQuantity(
                    bot.getMeso(),
                    minimumMesoReserve,
                    price,
                    remaining,
                    batchSize,
                    remainingBudget);
            if (affordable <= 0) {
                reason = AgentShopShortfallReason.NO_MESO;
                break;
            }
            short qty = (short) affordable;
            Shop.TransactionResult result = AgentShopGatewayRuntime.shop()
                    .buy(bot, shop, item.slot, item.shopItem.getItemId(), qty);
            if (result == Shop.TransactionResult.SUCCESS) {
                totalBought += qty;
                continue;
            }
            if (result == Shop.TransactionResult.NOT_ENOUGH_MESO) {
                reason = AgentShopShortfallReason.NO_MESO;
            } else if (result == Shop.TransactionResult.NO_SPACE) {
                reason = AgentShopShortfallReason.NO_SPACE;
            } else {
                reason = AgentShopShortfallReason.OTHER;
            }
            break;
        }

        return new AgentShopBuyReport(item.shopItem.getItemId(), totalBought, desiredQuantity, reason);
    }

    static int affordableQuantity(
            int mesos, int minimumMesoReserve, int price, int remaining, int batchSize) {
        return affordableQuantity(
                mesos,
                minimumMesoReserve,
                price,
                remaining,
                batchSize,
                Integer.MAX_VALUE);
    }

    static int affordableQuantity(
            int mesos,
            int minimumMesoReserve,
            int price,
            int remaining,
            int batchSize,
            int maximumSpend) {
        if (price <= 0 || remaining <= 0 || batchSize <= 0) {
            return 0;
        }
        int spendableMesos = Math.max(0, mesos - Math.max(0, minimumMesoReserve));
        int boundedSpend = Math.min(spendableMesos, Math.max(0, maximumSpend));
        return Math.min(Math.min(remaining, batchSize), boundedSpend / price);
    }

    private static String buildShortfallMessage(AgentShopBuyReport report, InventoryGateway inventory) {
        String itemName = resolveItemName(report.itemId(), "item", inventory);
        String got = GameConstants.numberWithCommas(report.quantity());
        String want = GameConstants.numberWithCommas(report.requestedQuantity());
        return switch (report.reason()) {
            case NO_SPACE -> AgentDialogueCatalog.shopNoSpaceReply(itemName, got, want, report.quantity());
            case OTHER -> AgentDialogueCatalog.shopRefusedReply(itemName);
            case NO_MESO, NONE -> AgentDialogueCatalog.shopNoMesoReply(itemName, got, want, report.quantity());
        };
    }

    private static String resolveItemName(int itemId, String fallbackName, InventoryGateway inventory) {
        String name = inventory.getItemName(itemId);
        return name != null ? name : fallbackName;
    }

    private static boolean isShopSequenceValid(AgentRuntimeEntry entry, Character bot, Point npcPos) {
        if (bot.getMap() == null) {
            return false;
        }
        // Accept proximity to the approach point OR the NPC itself: the sequence can start
        // via the stuck-at-NPC fallback, where the bot is near the NPC but not the approach point.
        return AgentShopStateRuntime.sequenceValid(entry, bot.getPosition(), npcPos, SHOP_ARRIVE_DIST)
                && findNpcNear(bot, npcPos) != null;
    }

    public static void cancelShopVisit(AgentRuntimeEntry entry) {
        clearShopState(entry);
    }

    // Abort the shop visit and tell the owner why. Player commands cancel via
    // cancelShopVisit (which clears shopVisitPending) and scheduleShopStep guards on
    // that flag, so a cleared flag here means a concurrent player cancel — stay silent.
    private static void abortShop(AgentRuntimeEntry entry, Character bot, String reason) {
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentShopRuntime.sayMapNow(bot, reason);
        }
        AgentShopStateRuntime.blockWorkflow(entry, reason, System.currentTimeMillis());
        clearShopState(entry);
    }

    private static void clearShopState(AgentRuntimeEntry entry) {
        AgentShopStateRuntime.clearShopState(entry);
    }

    private static long stepDelayMs() {
        return AgentShopRuntime.randomDelayMs(SHOP_STEP_DELAY_MIN_MS, SHOP_STEP_DELAY_MAX_MS);
    }

    private static void scheduleShopStep(AgentRuntimeEntry entry, Runnable step) {
        scheduleShopStep(entry, stepDelayMs(), step);
    }

    private static void scheduleShopStep(AgentRuntimeEntry entry, long delayMs, Runnable step) {
        AgentShopRuntime.afterDelay(entry, delayMs, () -> {
            if (!AgentShopStateRuntime.shouldRunScheduledShopStep(entry)) {
                return;
            }
            try {
                step.run();
            } catch (RuntimeException exception) {
                abortShop(entry, AgentRuntimeIdentityRuntime.bot(entry), AgentDialogueCatalog.shopScheduleErrorReply());
                throw exception;
            }
        });
    }

    private static Point pickShopApproachPoint(Point npcPos, AgentRuntimeEntry entry, Character bot) {
        var footholds = bot.getMap().getFootholds();
        if (footholds == null) {
            return npcPos;
        }
        List<Point> candidates = AgentShopApproachPolicy.footholdCandidatesNear(
                npcPos, footholds.getAllFootholds(), SHOP_MANHATTAN_RADIUS);
        if (candidates.isEmpty()) {
            return npcPos;
        }
        AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, bot);
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
                            || !AgentNavigationPathService.findPathForApproachProbe(graph, bot.getMap(), botPos,
                                    startRegionId, targetRegionId, candidate).isEmpty()) {
                        reachable.add(candidate);
                    }
                }
                if (!reachable.isEmpty()) {
                    candidates = reachable;
                }
            }
        }
        Point botPosition = bot.getPosition();
        candidates.sort(java.util.Comparator
                .comparingInt((Point candidate) -> Math.abs(candidate.y - npcPos.y))
                .thenComparingInt(candidate -> manhattan(candidate, npcPos))
                .thenComparingInt(candidate -> manhattan(candidate, botPosition))
                .thenComparingInt(candidate -> candidate.x));
        return candidates.get(0);
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
