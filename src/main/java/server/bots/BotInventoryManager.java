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
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentManualPeerTradeService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeBetweenBatchService;
import server.agents.capabilities.trade.AgentTradeCancellationService;
import server.agents.capabilities.trade.AgentTradeClosedWindowService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeCompletionService;
import server.agents.capabilities.trade.AgentTradeConfirmWaitService;
import server.agents.capabilities.trade.AgentTradeItemAddTickService;
import server.agents.capabilities.trade.AgentTradeInviteWaitService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeResetService;
import server.agents.capabilities.trade.AgentTradeSequenceOrchestrator;
import server.agents.capabilities.trade.AgentTradeTickService;
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
import java.util.concurrent.ThreadLocalRandom;

public class BotInventoryManager {
    private static final Logger log = LoggerFactory.getLogger(BotInventoryManager.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;
    private static final int MANUAL_TRADE_TIMEOUT_MS = 60_000;
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
            AgentManualTradeService.clearState(entry, bot);
            return;
        }

        if (AgentManualTradeService.beginOrTickTimeout(
                entry,
                bot,
                trade,
                MANUAL_TRADE_TIMEOUT_MS,
                BotMovementManager::tickDown)) {
            return;
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
        if (AgentManualPeerTradeService.tickPeerTrade(
                entry,
                bot,
                owner,
                trade,
                isOwnerTrade,
                AgentManualPeerTradeService.PeerTradeCallbacks.of(
                        peer -> peer.getClient() instanceof client.BotClient,
                        (peerId, ownerId) -> AgentOwnershipService.getInstance().isAuthorizedOwner(peerId, ownerId),
                        (inviter, pendingTrade) -> AgentManualTradeService.acceptInviteWhenReady(
                                entry,
                                bot,
                                inviter,
                                pendingTrade,
                                500 + BotMovementManager.cfg.TICK_MS,
                                BotMovementManager::tickDown),
                        completedTrade -> completeTradeAndThank(entry, bot, completedTrade),
                        peerOwner -> BotEquipManager.autoEquip(bot, peerOwner, null),
                        AgentManualTradeService::clearGreeting))) {
            return;
        }

        if (!trade.isFullTrade()) {
            // Only accept on bot's behalf when the owner was the initiator (bot is slot 1).
            // When bot is slot 0 (bot initiated via "trade me"), wait for owner to accept.
            trade = AgentManualTradeService.acceptInviteWhenReady(
                    entry,
                    bot,
                    owner,
                    trade,
                    500 + BotMovementManager.cfg.TICK_MS,
                    BotMovementManager::tickDown);
            if (trade == null || !trade.isFullTrade()) return;
        }

        AgentManualTradeService.sendGreetingOnce(bot, trade, () -> BotManager.getInstance().manualTradeGreeting());

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
        AgentInventoryTransferService.startTradeTransfer(category, entry, bot);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        AgentInventoryTransferService.startTradeTransfer(item, recipient, entry, bot);
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
        AgentTradeSequenceOrchestrator.startTradeSequence(
                category,
                recipient,
                items,
                mesos,
                singleBatch,
                entry,
                bot,
                tradeSequenceCallbacks(entry, bot));
    }

    private static void openTradeBatch(BotEntry entry, Character bot, List<Item> items, int mesos) {
        AgentTradeSequenceOrchestrator.openTradeBatch(
                entry,
                bot,
                items,
                mesos,
                tradeSequenceCallbacks(entry, bot));
    }

    private static AgentTradeSequenceOrchestrator.SequenceCallbacks tradeSequenceCallbacks(BotEntry entry, Character bot) {
        return AgentTradeSequenceOrchestrator.SequenceCallbacks.of(
                () -> AgentTradeRecipientService.resolveTradeRecipient(entry, bot),
                () -> cancelTradeSequence(entry, bot, "can't trade right now, stopping"),
                () -> Trade.startTrade(bot),
                Trade::inviteTrade,
                () -> BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()),
                message -> AgentBotInventoryRuntime.replyNow(entry, message));
    }

    /** Called every bot simulation tick while a trade sequence is in progress. */
    static void tickTrade(BotEntry entry, Character bot) {
        AgentTradeTickService.tickTrade(
                entry,
                bot,
                AgentTradeTickService.TradeTickCallbacks.of(
                        BotMovementManager::tickDown,
                        bot::getTrade,
                        () -> AgentTradeBetweenBatchService.tickBetweenBatches(
                                entry,
                                AgentTradeBetweenBatchService.BetweenBatchCallbacks.of(
                                        BotMovementManager::tickDown,
                                        category -> collectItems(category, entry, bot),
                                        category -> nextEquipsGroup(category, entry, bot),
                                        category -> nextAmmoGroup(category, bot),
                                        BotInventoryManager::equipsGroupMsg,
                                        items -> openTradeBatch(entry, bot, items, 0),
                                        () -> resetTradeState(entry, bot))),
                        () -> AgentTradeClosedWindowService.handleClosedTrade(
                                entry,
                                () -> BotMovementManager.delayAfterCurrentTick(1_000),
                                () -> resetTradeState(entry, bot),
                                () -> BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null)),
                        trade -> AgentTradeInviteWaitService.tickWaitingForAccept(
                                entry,
                                bot,
                                BotMovementManager.cfg.TICK_MS,
                                () -> resetTradeState(entry, bot)),
                        trade -> AgentTradeItemAddTickService.tickAddingItems(
                                entry,
                                bot,
                                trade,
                                AgentTradeItemAddTickService.ItemAddTickCallbacks.of(
                                        BotMovementManager::tickDown,
                                        () -> cancelTradeSequence(entry, bot, "don't have that many mesos anymore"),
                                        () -> BotMovementManager.delayAfterCurrentTick(500),
                                        () -> BotManager.randomReply(AgentDialogueCatalog.tradeAllDoneReplies()),
                                        () -> BotMovementManager.delayAfterCurrentTick(600),
                                        () -> BotMovementManager.delayAfterCurrentTick(500))),
                        trade -> AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                                entry,
                                bot,
                                trade,
                                BotMovementManager.cfg.TICK_MS,
                                () -> AgentTradeRecipientService.resolveTradeRecipient(entry, bot),
                                recipient -> recipient.getClient() instanceof client.BotClient,
                                () -> completeTradeAndThank(entry, bot, trade),
                                () -> resetTradeState(entry, bot))));
    }

    private static void cancelTradeSequence(BotEntry entry, Character bot, String msg) {
        AgentTradeCancellationService.cancelSequence(entry, bot, msg, () -> resetTradeState(entry, bot));
    }

    private static void clearManualTradeState(BotEntry entry, Character bot) {
        AgentManualTradeService.clearState(entry, bot);
    }

    private static void resetTradeState(BotEntry entry, Character bot) {
        AgentTradeResetService.reset(
                entry,
                bot,
                () -> AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, bot),
                () -> clearManualTradeState(entry, bot),
                () -> BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null));
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

    // ─── Item collection helpers ──────────────────────────────────────────────

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
