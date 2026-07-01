package server.bots;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.Item;
import config.YamlConfig;
import server.agents.capabilities.inventory.AgentAmmoTradeCallbackService;
import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeCallbackService;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquipTradeSlowLogService;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.looting.AgentPassiveLootCallbackService;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.looting.AgentPassiveLootService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentManualTradeCallbackService;
import server.agents.capabilities.trade.AgentManualOwnerTradeService;
import server.agents.capabilities.trade.AgentManualPeerTradeService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentManualTradeTickService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeBetweenBatchCallbackService;
import server.agents.capabilities.trade.AgentTradeBetweenBatchService;
import server.agents.capabilities.trade.AgentTradeClosedWindowService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeConfirmWaitService;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.capabilities.trade.AgentTradeGroupNavigationService;
import server.agents.capabilities.trade.AgentTradeItemAddTickService;
import server.agents.capabilities.trade.AgentTradeItemAddTickCallbackService;
import server.agents.capabilities.trade.AgentTradeItemCollectionCallbackService;
import server.agents.capabilities.trade.AgentTradeItemCollectionService;
import server.agents.capabilities.trade.AgentTradeInviteWaitService;
import server.agents.capabilities.trade.AgentTradeLifecycleCallbackService;
import server.agents.capabilities.trade.AgentTradeLifecycleService;
import server.agents.capabilities.trade.AgentTradeRecommendationService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeSequenceRuntimeService;
import server.agents.capabilities.trade.AgentTradeTickCallbackService;
import server.agents.capabilities.trade.AgentTradeTickService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityCallbackService;
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
    static void tickPassiveLoot(BotEntry entry, Character bot) {
        AgentPassiveLootService.tickPassiveLoot(
                entry,
                bot,
                AgentPassiveLootCallbackService.passiveLootCallbacks(
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
                AgentManualTradeCallbackService.manualTradeTickCallbacks(
                        () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                        Character::getTrade,
                        agent -> AgentManualTradeService.clearState(entry, agent),
                        (agent, trade) -> AgentManualTradeService.beginOrTickTimeout(
                                entry,
                                agent,
                                trade,
                                BotMovementManager::tickDown),
                        Character::getTrade,
                        (agent, tradeOwner, trade, isOwnerTrade) -> AgentManualPeerTradeService.tickPeerTrade(
                                entry,
                                agent,
                                tradeOwner,
                                trade,
                                isOwnerTrade,
                                AgentManualTradeCallbackService.peerTradeCallbacks(
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
                                AgentManualTradeCallbackService.ownerTradeCallbacks(
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
                AgentTradeTransferAvailabilityCallbackService.transferAvailabilityCallbacks(
                        fragment -> 0,
                        fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                        () -> collectItems(category, entry, bot)));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        return AgentTradeTransferAvailabilityService.countTransferableItems(
                category,
                bot,
                AgentTradeTransferAvailabilityCallbackService.transferAvailabilityCallbacks(
                        fragment -> AgentInventoryNamedItemService.countNamedItems(bot, fragment),
                        fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(bot, fragment, BotEquipManager::slotsFromName),
                        () -> collectItems(category, entry, bot)));
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
                                AgentTradeBetweenBatchCallbackService.betweenBatchCallbacks(
                                        BotMovementManager::tickDown,
                                        category -> collectItems(category, entry, bot),
                                        category -> AgentTradeGroupNavigationService.nextEquipsGroup(
                                                category,
                                                () -> classifyEquipTradeGroups(entry, bot)),
                                        category -> AgentTradeGroupNavigationService.nextAmmoGroup(
                                                category,
                                                () -> classifyAmmoTradeGroups(bot)),
                                        AgentInventoryTransferService::equipsGroupMessage,
                                        items -> AgentTradeSequenceRuntimeService.openTradeBatch(
                                                entry,
                                                bot,
                                                items,
                                                0,
                                                () -> cancelTradeSequence(entry, bot, "can't trade right now, stopping")),
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
                                AgentTradeItemAddTickCallbackService.itemAddTickCallbacks(
                                        BotMovementManager::tickDown,
                                        () -> cancelTradeSequence(entry, bot, "don't have that many mesos anymore"),
                                        () -> BotMovementManager.delayAfterCurrentTick(500),
                                        AgentTradeDialogueService::allDoneReply,
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
        AgentTradeLifecycleService.cancelTradeSequence(entry, bot, msg, tradeLifecycleCallbacks());
    }

    private static void clearManualTradeState(BotEntry entry, Character bot) {
        AgentTradeLifecycleService.clearManualTradeState(entry, bot, tradeLifecycleCallbacks());
    }

    private static void resetTradeState(BotEntry entry, Character bot) {
        AgentTradeLifecycleService.resetTradeState(entry, bot, tradeLifecycleCallbacks());
    }

    private static void completeTradeAndThank(BotEntry entry, Character bot, Trade trade) {
        AgentTradeLifecycleService.completeTradeAndReact(entry, bot, trade, tradeLifecycleCallbacks());
    }

    private static AgentTradeLifecycleService.LifecycleCallbacks tradeLifecycleCallbacks() {
        return AgentTradeLifecycleCallbackService.lifecycleCallbacks(
                AgentEquippedSlotTradeService::restoreTemporarilyUnequippedItems,
                AgentManualTradeService::clearState,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null),
                BotManager::randMs,
                AgentTradeDialogueService::thanksReply,
                AgentTradeDialogueService::freebieReply,
                () -> ThreadLocalRandom.current().nextInt(100),
                () -> ThreadLocalRandom.current().nextBoolean());
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static List<Item> collectItems(String category, BotEntry entry, Character bot) {
        return AgentTradeItemCollectionService.collectItems(
                category,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentTradeItemCollectionCallbackService.tradeItemCollectionCallbacks(
                        () -> AgentTradeRecommendationService.recommendedItems(
                                AgentBotRuntimeIdentityRuntime.owner(entry),
                                bot,
                                BotEquipManager::collectRecommendedItems),
                        () -> classifyEquipTradeGroups(entry, bot),
                        () -> classifyAmmoTradeGroups(bot)));
    }

    // ─── Drop actions (floor) ─────────────────────────────────────────────────

    // ─── Inventory info ───────────────────────────────────────────────────────

    // ─── Internals ────────────────────────────────────────────────────────────

    private static AmmoTradeGroups classifyAmmoTradeGroups(Character bot) {
        return AgentAmmoTradeClassificationService.classifyAmmoTradeGroups(
                bot,
                AgentAmmoTradeCallbackService.ammoTradeCallbacks(
                        () -> AgentAttackExecutionProvider.getEquippedWeaponType(bot),
                        ItemInformationProvider.getInstance()::getWatkForProjectile,
                        ItemInformationProvider.getInstance()::isQuestItem,
                        () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE));
    }

    private static AgentEquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character bot) {
        return AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                bot,
                AgentEquipTradeCallbackService.equipTradeCallbacks(
                        () -> AgentTradeCommandProfiler.profileCategory("equips"),
                        AgentEquipTradeSlowLogService::slowWarnNs,
                        AgentEquipTradeClassificationService.ClassificationCallbacks::collectEquipBag,
                        BotEquipManager::collectPotentialSelfUpgradeItems,
                        item -> AgentOfferService.isReservedForOtherRecipients(entry, bot, item),
                        () -> AgentBotRuntimeIdentityRuntime.owner(entry),
                        AgentEquipTradeSlowLogService::warnSlowClassification));
    }

    // ─── Pot-share helpers ────────────────────────────────────────────────────

}
