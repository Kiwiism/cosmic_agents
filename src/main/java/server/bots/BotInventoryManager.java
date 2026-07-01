package server.bots;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import server.agents.capabilities.looting.AgentLootEligibility;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeClassification;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.inventory.AgentInventoryCollectionService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.AmmoGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.trade.AgentDirectItemTradeService;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentMesoTradeService;
import server.agents.capabilities.trade.AgentTradeAllItemsAddedService;
import server.agents.capabilities.trade.AgentTradeBatchService;
import server.agents.capabilities.trade.AgentTradeCancellationService;
import server.agents.capabilities.trade.AgentTradeCategoryAnnouncementService;
import server.agents.capabilities.trade.AgentTradeClosedWindowService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeCompletionService;
import server.agents.capabilities.trade.AgentTradeConfirmWaitService;
import server.agents.capabilities.trade.AgentTradeItemAddService;
import server.agents.capabilities.trade.AgentTradeInviteWaitService;
import server.agents.capabilities.trade.AgentTradeMesoAddService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeResetService;
import server.agents.capabilities.trade.AgentTradeSequenceService;
import server.agents.capabilities.trade.AgentTradeStateService;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.ItemInformationProvider;
import server.Trade;
import server.maps.MapItem;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BotInventoryManager {
    private static final Logger log = LoggerFactory.getLogger(BotInventoryManager.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;
    private static final int MANUAL_TRADE_TIMEOUT_MS = 60_000;
    private static final Set<Integer> manualTradeGreetingSent = ConcurrentHashMap.newKeySet();
    static void tickPassiveLoot(BotEntry entry, Character bot) {
        if (AgentBotInventoryStateRuntime.hasLootInhibit(entry)) {
            AgentBotInventoryStateRuntime.tickLootInhibit(entry, BotMovementManager::tickDown);
            return;
        }
        if (AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)) {
            return;
        }

        AgentBotInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, BotMovementManager::tickDown);
        Point botPos = bot.getPosition();
        long now = System.currentTimeMillis();
        for (MapItem drop : bot.getMap().getDroppedItems()) {
            if (!AgentLootEligibility.isPresent(bot.getMap(), drop)) {
                AgentLootCleanupService.cleanupGhostDrop(bot, drop);
                continue;
            }

            Point dropPos = drop.getPosition();
            if (Math.abs(dropPos.x - botPos.x) > BotManager.cfg.LOOT_RADIUS
                    || Math.abs(dropPos.y - botPos.y) > BotManager.cfg.LOOT_RADIUS) {
                continue;
            }

            if (!AgentLootEligibility.canBotTargetLoot(entry, bot, bot.getMap(), drop, now)) {
                if (AgentLootEligibility.canBotLoot(entry, bot, drop)) {
                    continue;
                }
                if (drop.getMeso() <= 0 && drop.getItemId() > 0) {
                    InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
                    Inventory inventory = bot.getInventory(type);
                    if (inventory != null && inventory.isFull() && AgentBotInventoryStateRuntime.canWarnInventoryFull(entry)) {
                        AgentBotInventoryRuntime.replyNow(entry, type.name().toLowerCase() + " inventory is full!");
                        AgentBotInventoryStateRuntime.setInventoryFullWarnCooldownMs(
                                entry,
                                BotMovementManager.delayAfterCurrentTick(BotManager.cfg.INV_FULL_WARN_CD_MS));
                    }
                }
                continue;
            }

            if (drop.getMeso() <= 0 && drop.getItemId() > 0) {
                InventoryType type = ItemConstants.getInventoryType(drop.getItemId());
                Inventory inventory = bot.getInventory(type);
                if (inventory != null && inventory.isFull()) {
                    if (AgentBotInventoryStateRuntime.canWarnInventoryFull(entry)) {
                        AgentBotInventoryRuntime.replyNow(entry, type.name().toLowerCase() + " inventory is full!");
                        AgentBotInventoryStateRuntime.setInventoryFullWarnCooldownMs(
                                entry,
                                BotMovementManager.delayAfterCurrentTick(BotManager.cfg.INV_FULL_WARN_CD_MS));
                    }
                    continue;
                }
            }

            Item pickedItem = drop.getItem();
            int pickedItemId = drop.getItemId();
            Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
            if (ItemId.isNxCard(pickedItemId) && owner != null && owner.getMap() == bot.getMap()) {
                owner.pickupItem(drop);
            } else {
                bot.pickupItem(drop);
            }
            AgentLootCleanupService.cleanupGhostDrop(bot, drop);
            if (pickedItem != null && pickedItemId > 0 && AgentInventoryItemPolicy.hasItem(bot, pickedItem)) {
                InventoryType pickedType = ItemConstants.getInventoryType(pickedItemId);
                if (pickedType == InventoryType.EQUIP) {
                    BotEquipManager.autoEquip(bot, owner, AgentBotOfferStateRuntime.pendingLootOfferItem(entry));
                    if (AgentInventoryItemPolicy.hasItem(bot, pickedItem)) {
                        AgentOfferService.scheduleLootOfferPrompt(entry, bot, pickedItem, 5_000L);
                    }
                } else if (ItemConstants.isThrowingStar(pickedItemId)) {
                    AgentOfferService.scheduleLootOfferPrompt(entry, bot, pickedItem, 5_000L);
                }
            }
        }
    }

    static void tickManualTrade(BotEntry entry, Character bot) {
        if (AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)) return;

        Trade trade = bot.getTrade();
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (trade == null) {
            clearManualTradeState(entry, bot);
            return;
        }

        if (trade != AgentBotManualTradeStateRuntime.tradeRef(entry)) {
            manualTradeGreetingSent.remove(bot.getId());
            AgentBotManualTradeStateRuntime.beginTrade(entry, trade, MANUAL_TRADE_TIMEOUT_MS);
        } else if (AgentBotManualTradeStateRuntime.timeoutMs(entry) > 0) {
            AgentBotManualTradeStateRuntime.setTimeoutMs(
                    entry,
                    BotMovementManager.tickDown(AgentBotManualTradeStateRuntime.timeoutMs(entry)));
            if (AgentBotManualTradeStateRuntime.timeoutMs(entry) == 0) {
                Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
                clearManualTradeState(entry, bot);
                return;
            }
        }

        if (owner == null) {
            return;
        }

        Trade ownerTrade = owner.getTrade();
        Trade partner = trade.getPartner();
        boolean isOwnerTrade = ownerTrade != null
                && partner == ownerTrade
                && ownerTrade.getPartner() == trade
                && owner.getId() == ownerTrade.getChr().getId();
        if (!isOwnerTrade) {
            // Handle peer-bot trade: same-owner bot offering an item to this bot
            boolean isPeerBotTrade = partner != null
                    && partner.getChr().getClient() instanceof client.BotClient
                    && owner != null
                    && AgentOwnershipService.getInstance().isAuthorizedOwner(partner.getChr().getId(), owner.getId());
            if (!isPeerBotTrade) {
                manualTradeGreetingSent.remove(bot.getId());
                return;
            }
            // Accept invite if not yet joined — small delay so it feels human
            if (!trade.isFullTrade()) {
                if (trade.getNumber() != 1) return;
                AgentBotManualTradeStateRuntime.ensureAcceptDelay(entry, 500 + BotMovementManager.cfg.TICK_MS);
                AgentBotManualTradeStateRuntime.setAcceptDelayMs(
                        entry,
                        BotMovementManager.tickDown(AgentBotManualTradeStateRuntime.acceptDelayMs(entry)));
                if (AgentBotManualTradeStateRuntime.acceptDelayMs(entry) > 0) return;
                Trade.visitTrade(bot, partner.getChr());
                trade = bot.getTrade();
                if (trade == null || !trade.isFullTrade()) return;
            }
            // Confirm once the offering bot has confirmed its side
            if (trade.isPartnerConfirmed()) {
                completeTradeAndThank(entry, bot, trade);
                BotEquipManager.autoEquip(bot, owner, null);
            }
            return;
        }

        if (!trade.isFullTrade()) {
            // Only accept on bot's behalf when the owner was the initiator (bot is slot 1).
            // When bot is slot 0 (bot initiated via "trade me"), wait for owner to accept.
            if (trade.getNumber() != 1) return;
            AgentBotManualTradeStateRuntime.ensureAcceptDelay(entry, 500 + BotMovementManager.cfg.TICK_MS);
            AgentBotManualTradeStateRuntime.setAcceptDelayMs(
                    entry,
                    BotMovementManager.tickDown(AgentBotManualTradeStateRuntime.acceptDelayMs(entry)));
            if (AgentBotManualTradeStateRuntime.acceptDelayMs(entry) > 0) return;
            Trade.visitTrade(bot, owner);
            trade = bot.getTrade();
            if (trade == null || !trade.isFullTrade()) return;
        }

        if (manualTradeGreetingSent.add(bot.getId())) {
            trade.chat(BotManager.getInstance().manualTradeGreeting());
        }

        if (trade.isPartnerConfirmed()) {
            completeTradeAndThank(entry, bot, trade);
            BotEquipManager.autoEquip(bot, owner, null);
        }
    }

    // ─── Entry point from chat choice ─────────────────────────────────────────

    /**
     * Called after the owner chooses "drop" or "trade" in the item-choice prompt.
     * category: "scrolls", "pots", "equips", "etc", or "name:<fragment>"
     */
    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character bot) {
        AgentInventoryTransferService.executeChoice(category, tradeToOwner, entry, bot);
    }

    // ─── Trade actions (actual trade window) ─────────────────────────────────

    /**
     * Kicks off a trade sequence for the given category.
     * Items are batched ≤9 per trade window; subsequent batches open new trades automatically.
     */
    public static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        long startedAt = AgentTradeCommandProfiler.profileCategory(category) ? System.nanoTime() : 0L;
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            startTradeMesoTransfer(category, entry, bot);
            return;
        }
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeOwnerNotFoundReply());
            return;
        }
        if (bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeBotBusyReply());
            return;
        }
        if (owner.getTrade() != null) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeOwnerBusyReply());
            return;
        }
        if ("equips".equals(category)) {
            long equipsStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
            startEquipsGroupTradeTransfer(owner, entry, bot);
            AgentTradeCommandProfiler.logSlowCommand(category, "startEquipsGroupTradeTransfer", entry, bot, equipsStartedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
            AgentTradeCommandProfiler.logSlowCommand(category, "startTradeTransfer", entry, bot, startedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
            return;
        }
        if (AgentInventoryTradePolicy.isReservedEquipsCategory(category)) {
            List<Item> items = collectReservedEquipTradePage(category, entry, bot);
            if (items.isEmpty()) {
                AgentBotInventoryRuntime.replyNow(entry, AgentInventoryDialogueReporter.noItemsReply(category));
                return;
            }
            startTradeSequence(category, owner, items, 0, true, entry, bot);
            AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, reservedEquipsPageMessage(category, entry, bot));
            AgentTradeCommandProfiler.logSlowCommand(category, "startTradeTransfer", entry, bot, startedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
            return;
        }
        if ("ammo".equals(category)) {
            startAmmoGroupTradeTransfer(owner, entry, bot);
            AgentTradeCommandProfiler.logSlowCommand(category, "startTradeTransfer", entry, bot, startedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
            return;
        }
        long prepareStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        PreparedTradeItems prepared = prepareTradeItems(category, entry, bot);
        AgentTradeCommandProfiler.logSlowCommand(category, "prepareTradeItems", entry, bot, prepareStartedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
        if (prepared.errorMessage() != null) {
            AgentBotInventoryRuntime.replyNow(entry, prepared.errorMessage());
            return;
        }
        List<Item> items = prepared.items();
        if (items.isEmpty()) {
            AgentBotInventoryRuntime.replyNow(entry, AgentInventoryDialogueReporter.noItemsReply(category));
            return;
        }
        startTradeSequence(category, owner, items, 0, AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry), entry, bot);
        AgentTradeCommandProfiler.logSlowCommand(category, "startTradeTransfer", entry, bot, startedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        AgentDirectItemTradeService.DirectItemTradeDecision decision = AgentDirectItemTradeService.decideStart(
                recipient != null,
                AgentInventoryItemPolicy.hasItem(bot, item),
                bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                recipient != null && recipient.getTrade() != null);
        if (decision.action() == AgentDirectItemTradeService.Action.REPLY) {
            AgentBotInventoryRuntime.replyNow(entry, decision.reply());
            return;
        }
        if (decision.action() == AgentDirectItemTradeService.Action.RETRY) {
            AgentBotPendingTradeStateRuntime.queueRetry(
                    entry,
                    () -> startTradeTransfer(item, recipient, entry, bot),
                    BotMovementManager.delayAfterCurrentTick(10_000));
            return;
        }
        startTradeSequence("loot_offer", recipient, List.of(item), 0, true, entry, bot);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTradeCollectionService.hasTransferableItems(
                category,
                bot,
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                () -> collectItems(category, entry, bot));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTradeCollectionService.countTransferableItems(
                category,
                bot,
                fragment -> AgentInventoryNamedItemService.countNamedItems(bot, fragment),
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                () -> AgentInventoryTradePolicy.itemQuantitySum(collectItems(category, entry, bot)));
    }

    /** Opens a trade for the first ≤9 items; remaining items are re-collected next batch. */
    private static void startTradeSequence(String category,
                                           Character recipient,
                                           List<Item> items,
                                           int mesos,
                                           boolean singleBatch,
                                           BotEntry entry,
                                           Character bot) {
        AgentTradeSequenceService.startSequence(
                category,
                recipient,
                items,
                mesos,
                singleBatch,
                entry,
                (batchItems, batchMesos) -> openTradeBatch(entry, bot, batchItems, batchMesos));
    }

    private static void openTradeBatch(BotEntry entry, Character bot, List<Item> items, int mesos) {
        AgentTradeBatchService.openBatch(
                entry,
                bot,
                items,
                mesos,
                () -> AgentTradeRecipientService.resolveTradeRecipient(entry, bot),
                () -> cancelTradeSequence(entry, bot, "can't trade right now, stopping"),
                () -> Trade.startTrade(bot),
                Trade::inviteTrade,
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()),
                message -> AgentBotInventoryRuntime.replyNow(entry, message));
    }

    /** Called every bot simulation tick while a trade sequence is in progress. */
    static void tickTrade(BotEntry entry, Character bot) {
        // Fire a queued bot-initiated retry once this bot is free and the delay expires.
        if (AgentBotPendingTradeStateRuntime.isIdle(entry) && AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry)) {
            if (AgentBotPendingTradeStateRuntime.retryDelayMs(entry) > 0) {
                AgentBotPendingTradeStateRuntime.setRetryDelayMs(
                        entry,
                        BotMovementManager.tickDown(AgentBotPendingTradeStateRuntime.retryDelayMs(entry)));
                return;
            }
            Runnable retry = AgentBotPendingTradeStateRuntime.takeRetry(entry);
            retry.run();
            return;
        }
        if (AgentBotPendingTradeStateRuntime.isIdle(entry)) return;

        Trade trade = bot.getTrade();

        // ── PAUSE between batches (items == null) ──────────────────────────
        if (AgentBotPendingTradeStateRuntime.isBetweenBatches(entry)) {
            if (AgentBotPendingTradeStateRuntime.singleBatch(entry)) {
                resetTradeState(entry, bot);
                return;
            }
            if (AgentBotPendingTradeStateRuntime.timerMs(entry) > 0) {
                AgentBotPendingTradeStateRuntime.tickTimerDown(entry, BotMovementManager::tickDown);
                return;
            }
            List<Item> next = collectItems(AgentBotPendingTradeStateRuntime.category(entry), entry, bot);
            if (next.isEmpty()) {
                String advanced = nextEquipsGroup(AgentBotPendingTradeStateRuntime.category(entry), entry, bot);
                if (advanced == null) {
                    advanced = nextAmmoGroup(AgentBotPendingTradeStateRuntime.category(entry), bot);
                }
                if (advanced != null) {
                    AgentBotPendingTradeStateRuntime.setCategory(entry, advanced);
                    AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, equipsGroupMsg(advanced));
                    openTradeBatch(entry, bot, collectItems(advanced, entry, bot), 0);
                } else {
                    resetTradeState(entry, bot);
                }
            } else {
                openTradeBatch(entry, bot, next, 0);
            }
            return;
        }

        // ── Trade was closed externally ────────────────────────────────────
        if (trade == null) {
            if (AgentTradeClosedWindowService.handleClosedTrade(
                    entry,
                    () -> BotMovementManager.delayAfterCurrentTick(1_000),
                    () -> resetTradeState(entry, bot),
                    () -> BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null))) {
                return;
            }
            if (AgentBotPendingTradeStateRuntime.botDone(entry)) {
                // Both sides confirmed — sequence complete or cancelled after bot OK
                if (AgentBotPendingTradeStateRuntime.singleBatch(entry)) {
                    resetTradeState(entry, bot);
                    BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null);
                    return;
                }
                AgentTradeStateService.enterBetweenBatches(entry, BotMovementManager.delayAfterCurrentTick(1_000));
            } else if (AgentBotPendingTradeStateRuntime.allItemsAdded(entry)) {
                // Owner cancelled after items were added (items returned to bot)
                AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeCancelledReply());
                resetTradeState(entry, bot);
                BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null);
            } else {
                // Owner declined invite
                AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeDeclinedReply());
                resetTradeState(entry, bot);
            }
            return;
        }

        // ── WAITING FOR ACCEPT ────────────────────────────────────────────
        if (!trade.isFullTrade()) {
            AgentTradeInviteWaitService.tickWaitingForAccept(
                    entry,
                    bot,
                    BotMovementManager.cfg.TICK_MS,
                    () -> resetTradeState(entry, bot));
            return;
        }

        // ── ADDING ITEMS ──────────────────────────────────────────────────
        if (!AgentBotPendingTradeStateRuntime.allItemsAdded(entry)) {
            if (AgentBotPendingTradeStateRuntime.timerMs(entry) > 0) {
                AgentBotPendingTradeStateRuntime.tickTimerDown(entry, BotMovementManager::tickDown);
                return;
            }

            if (AgentTradeMesoAddService.handlePendingMeso(
                    entry,
                    bot,
                    trade,
                    () -> cancelTradeSequence(entry, bot, "don't have that many mesos anymore"),
                    () -> BotMovementManager.delayAfterCurrentTick(500))) {
                return;
            }

            List<Item> items = AgentBotPendingTradeStateRuntime.items(entry);
            int idx = AgentBotPendingTradeStateRuntime.itemIndex(entry);

            if (idx >= items.size()) {
                // All items added — say so in trade chat and wait for owner OK
                AgentTradeAllItemsAddedService.markCompleteIfNoMoreItems(
                        entry,
                        trade,
                        () -> BotManager.randomReply(AgentDialogueCatalog.tradeAllDoneReplies()));
                return;
            }

            if (AgentTradeCategoryAnnouncementService.announceBeforeFirstItem(
                    entry,
                    trade,
                    () -> BotMovementManager.delayAfterCurrentTick(600))) {
                return;
            }

            AgentTradeItemAddService.addNextItem(entry, bot, trade, BotMovementManager.delayAfterCurrentTick(500));
            return;
        }

        // ── WAITING FOR OWNER TO CLICK OK ─────────────────────────────────
        if (!AgentBotPendingTradeStateRuntime.botDone(entry)) {
            if (AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                    entry,
                    bot,
                    trade,
                    BotMovementManager.cfg.TICK_MS,
                    () -> AgentTradeRecipientService.resolveTradeRecipient(entry, bot),
                    recipient -> recipient.getClient() instanceof client.BotClient,
                    () -> completeTradeAndThank(entry, bot, trade),
                    () -> resetTradeState(entry, bot))) {
                return;
            }
            AgentBotPendingTradeStateRuntime.addTimerMs(entry, BotMovementManager.cfg.TICK_MS);
            Character recipient = AgentTradeRecipientService.resolveTradeRecipient(entry, bot);
            boolean recipientIsBot = recipient != null && recipient.getClient() instanceof client.BotClient;
            if (recipientIsBot || trade.isPartnerConfirmed()) {
                completeTradeAndThank(entry, bot, trade);
                AgentBotPendingTradeStateRuntime.markBotDone(entry);
                AgentBotPendingTradeStateRuntime.clearTimer(entry);
            } else if (AgentBotPendingTradeStateRuntime.timerMs(entry) > 60_000) { // 60 s timeout
                AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeConfirmTimeoutReply());
                Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
                resetTradeState(entry, bot);
            }
        }
        // pendingTradeBotDone=true: wait for bot.getTrade() to become null (handled above)
    }

    private static void cancelTradeSequence(BotEntry entry, Character bot, String msg) {
        AgentTradeCancellationService.cancelSequence(entry, bot, msg, () -> resetTradeState(entry, bot));
    }

    private static void clearManualTradeState(BotEntry entry, Character bot) {
        manualTradeGreetingSent.remove(bot.getId());
        AgentBotManualTradeStateRuntime.clear(entry);
    }

    private static void resetTradeState(BotEntry entry, Character bot) {
        AgentTradeResetService.reset(
                entry,
                bot,
                () -> AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, bot),
                () -> clearManualTradeState(entry, bot),
                () -> BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null));
    }

    private static void resetTradeStateLegacy(BotEntry entry, Character bot) {
        boolean hadRestores = AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry);
        AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, bot);
        clearManualTradeState(entry, bot);
        AgentTradeStateService.clearSequence(entry);
        // Safety net: if any items were temporarily unequipped for a trade that ended without
        // completing (declined invite / cancel / timeout), the per-slot restore above may fail
        // (slot occupied, item lost via window-swap bookkeeping). Re-run autoEquip so empty
        // slots get refilled from the bot's bag — prevents leaving the bot wearing e.g. pants
        // without a top after a declined trade.
        if (hadRestores && bot != null) {
            BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null);
        }
    }

    private static void completeTradeAndThank(BotEntry entry, Character bot, Trade trade) {
        AgentTradeCompletionService.completeAndReact(
                entry,
                bot,
                trade,
                () -> BotManager.randMs(800, 1300),
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeThanksReplies()),
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeFreebieReplies()),
                () -> ThreadLocalRandom.current().nextInt(100),
                () -> ThreadLocalRandom.current().nextBoolean());
    }

    private static void startTradeMesoTransfer(String category, BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentMesoTradeService.MesoTradeStartDecision decision = AgentMesoTradeService.decideStart(
                category,
                owner != null,
                bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                owner != null && owner.getTrade() != null,
                bot.getMeso());
        if (decision.shouldReply()) {
            AgentBotInventoryRuntime.replyNow(entry, decision.reply());
            return;
        }

        startTradeSequence(category, owner, List.of(), decision.mesos(), true, entry, bot);
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static PreparedTradeItems prepareTradeItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTradeCollectionService.prepareTradeItems(
                category,
                bot,
                fragment -> {
                    AgentEquippedSlotTradeService.PreparedTradeItems equippedSlotItems =
                            AgentEquippedSlotTradeService.prepareEquippedSlotTradeItems(
                                    fragment,
                                    entry,
                                    bot,
                                    BotEquipManager::slotsFromName,
                                    () -> AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, bot));
                    return new PreparedTradeItems(equippedSlotItems.items(), equippedSlotItems.errorMessage());
                },
                fragment -> AgentInventoryNamedItemService.collectNamedItems(bot, fragment),
                () -> recommendedItems(entry, bot),
                () -> classifyEquipTradeGroups(entry, bot),
                () -> classifyAmmoTradeGroups(bot),
                AgentBotRuntimeIdentityRuntime.owner(entry));
    }

    private static List<Item> collectItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTradeCollectionService.collectItems(
                category,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                () -> recommendedItems(entry, bot),
                () -> classifyEquipTradeGroups(entry, bot),
                () -> classifyAmmoTradeGroups(bot));
    }

    // ─── Drop actions (floor) ─────────────────────────────────────────────────

    // ─── Inventory info ───────────────────────────────────────────────────────

    // ─── Internals ────────────────────────────────────────────────────────────

    private static List<Item> collectReservedEquipTradePage(String category, BotEntry entry, Character bot) {
        return AgentEquipTradeGroupService.reservedEquipTradePage(category, classifyEquipTradeGroups(entry, bot));
    }

    private static String reservedEquipsPageMessage(String category, BotEntry entry, Character bot) {
        return AgentEquipTradeGroupService.reservedEquipsPageMessage(category, classifyEquipTradeGroups(entry, bot));
    }

    private static String equipsGroupMsg(String category) {
        return AgentEquipTradeGroupService.equipsGroupMessage(
                category,
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies()),
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies()));
    }

    private static String nextEquipsGroup(String category, BotEntry entry, Character bot) {
        return AgentEquipTradeGroupService.nextEquipsGroup(category, classifyEquipTradeGroups(entry, bot));
    }

    private static String nextAmmoGroup(String category, Character bot) {
        return AgentInventoryAmmoPolicy.nextAvailableGroupCategory(category, classifyAmmoTradeGroups(bot));
    }

    private static void startEquipsGroupTradeTransfer(Character owner, BotEntry entry, Character bot) {
        AgentEquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
        EquipsGroup group = AgentEquipTradeGroupService.firstAvailableGroup(groups);
        if (group != null) {
            String category = group.categoryString();
            startTradeSequence(category, owner, groups.itemsFor(group), 0, false, entry, bot);
            String msg = equipsGroupMsg(category);
            if (msg != null) AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, msg);
            return;
        }
        AgentBotInventoryRuntime.replyNow(entry, AgentInventoryDialogueReporter.noItemsReply("equips"));
    }

    private static void startAmmoGroupTradeTransfer(Character owner, BotEntry entry, Character bot) {
        AmmoTradeGroups groups = classifyAmmoTradeGroups(bot);
        AmmoGroup group = AgentInventoryAmmoPolicy.firstAvailableGroup(groups);
        if (group != null) {
            startTradeSequence(group.categoryString(), owner, groups.itemsFor(group), 0, false, entry, bot);
            return;
        }
        AgentBotInventoryRuntime.replyNow(entry, AgentInventoryDialogueReporter.noItemsReply("ammo"));
    }

    private static List<Item> recommendedItems(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        return owner != null ? BotEquipManager.collectRecommendedItems(owner, bot) : List.of();
    }

    private static AmmoTradeGroups classifyAmmoTradeGroups(Character bot) {
        return AgentInventoryAmmoPolicy.classifyTradeGroups(bot,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot),
                ItemInformationProvider.getInstance()::getWatkForProjectile,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
    }

    private static List<Item> collectTrashEquips(BotEntry entry, Character bot) {
        return classifyEquipTradeGroups(entry, bot).itemsFor(EquipsGroup.NORMAL);
    }

    private static AgentEquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character bot) {
        long startedAt = AgentTradeCommandProfiler.profileCategory("equips") ? System.nanoTime() : 0L;
        long bagScanStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        List<Item> all = new ArrayList<>();
        all.addAll(AgentInventoryCollectionService.collectFromBag(bot, InventoryType.EQUIP, item -> true));
        long bagScanNs = startedAt != 0L ? System.nanoTime() - bagScanStartedAt : 0L;
        long selfKeepStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        Set<Item> selfKeep = BotEquipManager.collectPotentialSelfUpgradeItems(bot);
        long selfKeepNs = startedAt != 0L ? System.nanoTime() - selfKeepStartedAt : 0L;

        AgentEquipTradeClassification classification = AgentEquipTradeGroupService.classifyEquipGroups(
                bot,
                all,
                selfKeep,
                item -> AgentOfferService.isReservedForOtherRecipients(entry, bot, item),
                startedAt != 0L);
        AgentEquipTradeGroups groups = classification.groups();
        if (startedAt != 0L) {
            long elapsedNs = System.nanoTime() - startedAt;
            if (elapsedNs >= TRADE_COMMAND_PROFILE_WARN_NS) {
                String botName = bot != null ? bot.getName() : "?";
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                String ownerName = owner != null ? owner.getName() : "?";
                log.warn(
                        "Slow equip trade classification: took {} ms bot={} owner={} bagItems={} selfKeep={} normalItems={} reservedOtherItems={} reservedSelfItems={} bagScanMs={} selfKeepMs={} reservedOtherMs={} reservedOtherChecks={} reservedOtherHits={} sortMs={}",
                        String.format("%.1f", elapsedNs / 1_000_000.0),
                        botName,
                        ownerName,
                        all.size(),
                        selfKeep.size(),
                        groups.normal().size(),
                        groups.reservedForOther().size(),
                        groups.reservedForSelf().size(),
                        String.format("%.1f", bagScanNs / 1_000_000.0),
                        String.format("%.1f", selfKeepNs / 1_000_000.0),
                        String.format("%.1f", classification.reservedOtherNs() / 1_000_000.0),
                        classification.reservedOtherChecks(),
                        classification.reservedOtherHits(),
                        String.format("%.1f", classification.sortNs() / 1_000_000.0));
            }
        }
        return groups;
    }

    private static boolean isOwnClassEquip(Character bot, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.isOwnClassEquip(bot, ii, equip);
    }

    // ─── Pot-share helpers ────────────────────────────────────────────────────

}
