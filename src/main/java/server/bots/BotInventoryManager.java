package server.bots;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.Item;
import config.YamlConfig;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.looting.AgentPassiveLootCallbackService;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.looting.AgentPassiveLootService;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentInventoryTradeRuntimeService;
import server.agents.capabilities.trade.AgentManualTradeRuntimeService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeTickRuntimeService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityCallbackService;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.ItemInformationProvider;

import java.util.List;

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
        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                manualTradeRuntimeCallbacks(entry),
                AgentTradeLifecycleRuntimeService.lifecycleCallbacks(tradeLifecycleRuntimeCallbacks()));
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
        AgentTradeTickRuntimeService.tickTrade(
                entry,
                bot,
                tradeTickRuntimeCallbacks(),
                tradeRuntimeCallbacks(entry, bot),
                tradeLifecycleRuntimeCallbacks());
    }

    private static AgentTradeTickRuntimeService.RuntimeCallbacks tradeTickRuntimeCallbacks() {
        return AgentTradeTickRuntimeService.RuntimeCallbacks.of(
                BotMovementManager::tickDown,
                Character::getTrade,
                BotMovementManager::delayAfterCurrentTick,
                () -> BotMovementManager.cfg.TICK_MS,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null),
                AgentTradeRecipientService::resolveTradeRecipient,
                recipient -> recipient.getClient() instanceof client.BotClient);
    }

    private static AgentTradeLifecycleRuntimeService.RuntimeCallbacks tradeLifecycleRuntimeCallbacks() {
        return AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                AgentEquippedSlotTradeService::restoreTemporarilyUnequippedItems,
                AgentManualTradeService::clearState,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null),
                BotManager::randMs,
                AgentTradeDialogueService::thanksReply,
                AgentTradeDialogueService::freebieReply);
    }

    private static AgentManualTradeRuntimeService.RuntimeCallbacks manualTradeRuntimeCallbacks(BotEntry entry) {
        return AgentManualTradeRuntimeService.RuntimeCallbacks.of(
                () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                BotMovementManager::tickDown,
                () -> BotMovementManager.cfg.TICK_MS,
                peer -> peer.getClient() instanceof client.BotClient,
                (peerId, ownerId) -> AgentOwnershipService.getInstance().isAuthorizedOwner(peerId, ownerId),
                () -> BotManager.getInstance().manualTradeGreeting(),
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null));
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static List<Item> collectItems(String category, BotEntry entry, Character bot) {
        return AgentInventoryTradeRuntimeService.collectItems(
                category,
                bot,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                tradeRuntimeCallbacks(entry, bot));
    }

    // ─── Drop actions (floor) ─────────────────────────────────────────────────

    // ─── Inventory info ───────────────────────────────────────────────────────

    // ─── Internals ────────────────────────────────────────────────────────────

    private static AgentInventoryTradeRuntimeService.RuntimeCallbacks tradeRuntimeCallbacks(BotEntry entry, Character bot) {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                BotEquipManager::collectRecommendedItems,
                AgentAttackExecutionProvider::getEquippedWeaponType,
                ItemInformationProvider.getInstance()::getWatkForProjectile,
                ItemInformationProvider.getInstance()::isQuestItem,
                () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE,
                () -> AgentTradeCommandProfiler.profileCategory("equips"),
                BotEquipManager::collectPotentialSelfUpgradeItems,
                item -> AgentOfferService.isReservedForOtherRecipients(entry, bot, item),
                () -> AgentBotRuntimeIdentityRuntime.owner(entry));
    }

    // ─── Pot-share helpers ────────────────────────────────────────────────────

}
