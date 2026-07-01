package server.bots;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.Item;
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.looting.AgentPassiveLootService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentManualOwnerTradeService;
import server.agents.capabilities.trade.AgentManualPeerTradeService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentManualTradeTickService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeBetweenBatchService;
import server.agents.capabilities.trade.AgentTradeCancellationService;
import server.agents.capabilities.trade.AgentTradeClosedWindowService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeCompletionService;
import server.agents.capabilities.trade.AgentTradeConfirmWaitService;
import server.agents.capabilities.trade.AgentTradeItemAddTickService;
import server.agents.capabilities.trade.AgentTradeItemCollectionService;
import server.agents.capabilities.trade.AgentTradeInviteWaitService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeResetService;
import server.agents.capabilities.trade.AgentTradeSequenceCallbackService;
import server.agents.capabilities.trade.AgentTradeSequenceOrchestrator;
import server.agents.capabilities.trade.AgentTradeTickCallbackService;
import server.agents.capabilities.trade.AgentTradeTickService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityService;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.ItemInformationProvider;
import server.Trade;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BotInventoryManager {
    private static final Logger log = LoggerFactory.getLogger(BotInventoryManager.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;
    private static final int MANUAL_TRADE_TIMEOUT_MS = 60_000;
    static void tickPassiveLoot(BotEntry entry, Character bot) {
        AgentPassiveLootService.tickPassiveLoot(
                entry,
                bot,
                AgentPassiveLootService.PassiveLootCallbacks.of(
                        () -> AgentBotInventoryStateRuntime.hasLootInhibit(entry),
                        () -> AgentBotInventoryStateRuntime.tickLootInhibit(entry, BotMovementManager::tickDown),
                        () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                        () -> AgentBotInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, BotMovementManager::tickDown),
                        System::currentTimeMillis,
                        () -> BotManager.cfg.LOOT_RADIUS,
                        () -> AgentBotInventoryStateRuntime.canWarnInventoryFull(entry),
                        AgentBotInventoryRuntime::replyNow,
                        () -> BotMovementManager.delayAfterCurrentTick(BotManager.cfg.INV_FULL_WARN_CD_MS),
                        cooldown -> AgentBotInventoryStateRuntime.setInventoryFullWarnCooldownMs(entry, cooldown),
                        () -> AgentBotRuntimeIdentityRuntime.owner(entry),
                        () -> AgentBotOfferStateRuntime.pendingLootOfferItem(entry),
                        AgentInventoryItemPolicy::hasItem,
                        BotEquipManager::autoEquip,
                        AgentOfferService::scheduleLootOfferPrompt,
                        AgentLootCleanupService::cleanupGhostDrop,
                        Character::pickupItem));
    }

    static void tickManualTrade(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentManualTradeTickService.tickManualTrade(
                bot,
                owner,
                AgentManualTradeTickService.ManualTradeTickCallbacks.of(
                        () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                        Character::getTrade,
                        agent -> AgentManualTradeService.clearState(entry, agent),
                        (agent, trade) -> AgentManualTradeService.beginOrTickTimeout(
                                entry,
                                agent,
                                trade,
                                MANUAL_TRADE_TIMEOUT_MS,
                                BotMovementManager::tickDown),
                        Character::getTrade,
                        (agent, tradeOwner, trade, isOwnerTrade) -> AgentManualPeerTradeService.tickPeerTrade(
                                entry,
                                agent,
                                tradeOwner,
                                trade,
                                isOwnerTrade,
                                AgentManualPeerTradeService.PeerTradeCallbacks.of(
                                        peer -> peer.getClient() instanceof client.BotClient,
                                        (peerId, ownerId) -> AgentOwnershipService.getInstance().isAuthorizedOwner(peerId, ownerId),
                                        (inviter, pendingTrade) -> AgentManualTradeService.acceptInviteWhenReady(
                                                entry,
                                                agent,
                                                inviter,
                                                pendingTrade,
                                                500 + BotMovementManager.cfg.TICK_MS,
                                                BotMovementManager::tickDown),
                                        completedTrade -> completeTradeAndThank(entry, agent, completedTrade),
                                        peerOwner -> BotEquipManager.autoEquip(agent, peerOwner, null),
                                        AgentManualTradeService::clearGreeting)),
                        (agent, tradeOwner, trade) -> AgentManualOwnerTradeService.tickOwnerTrade(
                                agent,
                                tradeOwner,
                                trade,
                                AgentManualOwnerTradeService.OwnerTradeCallbacks.of(
                                        (inviter, pendingTrade) -> AgentManualTradeService.acceptInviteWhenReady(
                                                entry,
                                                agent,
                                                inviter,
                                                pendingTrade,
                                                500 + BotMovementManager.cfg.TICK_MS,
                                                BotMovementManager::tickDown),
                                        AgentManualTradeService::sendGreetingOnce,
                                        () -> BotManager.getInstance().manualTradeGreeting(),
                                        completedTrade -> completeTradeAndThank(entry, agent, completedTrade),
                                        refillOwner -> BotEquipManager.autoEquip(agent, refillOwner, null)))));
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
        return AgentTradeTransferAvailabilityService.hasTransferableItems(
                category,
                bot,
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                () -> collectItems(category, entry, bot));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentTradeTransferAvailabilityService.countTransferableItems(
                category,
                bot,
                fragment -> AgentInventoryNamedItemService.countNamedItems(bot, fragment),
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                () -> collectItems(category, entry, bot));
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
        return AgentTradeSequenceCallbackService.sequenceCallbacks(
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
                AgentTradeTickCallbackService.tradeTickCallbacks(
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
        return AgentTradeItemCollectionService.collectItems(
                category,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentTradeItemCollectionService.TradeItemCollectionCallbacks.of(
                        () -> recommendedItems(entry, bot),
                        () -> classifyEquipTradeGroups(entry, bot),
                        () -> classifyAmmoTradeGroups(bot)));
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
        return AgentAmmoTradeClassificationService.nextAmmoGroup(category, classifyAmmoTradeGroups(bot));
    }

    private static List<Item> recommendedItems(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        return owner != null ? BotEquipManager.collectRecommendedItems(owner, bot) : List.of();
    }

    private static AmmoTradeGroups classifyAmmoTradeGroups(Character bot) {
        return AgentAmmoTradeClassificationService.classifyAmmoTradeGroups(
                bot,
                AgentAmmoTradeClassificationService.AmmoTradeCallbacks.of(
                        () -> AgentAttackExecutionProvider.getEquippedWeaponType(bot),
                        ItemInformationProvider.getInstance()::getWatkForProjectile,
                        ItemInformationProvider.getInstance()::isQuestItem,
                        () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE));
    }

    private static AgentEquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character bot) {
        return AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                bot,
                AgentEquipTradeClassificationService.ClassificationCallbacks.of(
                        () -> AgentTradeCommandProfiler.profileCategory("equips"),
                        () -> TRADE_COMMAND_PROFILE_WARN_NS,
                        AgentEquipTradeClassificationService.ClassificationCallbacks::collectEquipBag,
                        BotEquipManager::collectPotentialSelfUpgradeItems,
                        item -> AgentOfferService.isReservedForOtherRecipients(entry, bot, item),
                        () -> AgentBotRuntimeIdentityRuntime.owner(entry),
                        report -> log.warn(
                        "Slow equip trade classification: took {} ms bot={} owner={} bagItems={} selfKeep={} normalItems={} reservedOtherItems={} reservedSelfItems={} bagScanMs={} selfKeepMs={} reservedOtherMs={} reservedOtherChecks={} reservedOtherHits={} sortMs={}",
                        String.format("%.1f", report.elapsedNs() / 1_000_000.0),
                        report.agentName(),
                        report.ownerName(),
                        report.bagItems(),
                        report.selfKeep(),
                        report.normalItems(),
                        report.reservedOtherItems(),
                        report.reservedSelfItems(),
                        String.format("%.1f", report.bagScanNs() / 1_000_000.0),
                        String.format("%.1f", report.selfKeepNs() / 1_000_000.0),
                        String.format("%.1f", report.reservedOtherNs() / 1_000_000.0),
                        report.reservedOtherChecks(),
                        report.reservedOtherHits(),
                        String.format("%.1f", report.sortNs() / 1_000_000.0))));
    }

    // ─── Pot-share helpers ────────────────────────────────────────────────────

}
