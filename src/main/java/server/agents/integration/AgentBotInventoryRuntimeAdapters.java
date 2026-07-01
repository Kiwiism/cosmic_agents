package server.agents.integration;

import client.Character;
import config.YamlConfig;
import server.ItemInformationProvider;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.looting.AgentLootCleanupService;
import server.agents.capabilities.looting.AgentPassiveLootRuntimeService;
import server.agents.capabilities.trade.AgentInventoryTradeRuntimeService;
import server.agents.capabilities.trade.AgentManualTradeRuntimeService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.capabilities.trade.AgentTradeRecipientService;
import server.agents.capabilities.trade.AgentTradeTickRuntimeService;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityRuntimeService;
import server.agents.capabilities.equipment.AgentEquipRecommendation;
import server.bots.BotEntry;
import server.bots.BotEquipManager;
import server.bots.BotManager;
import server.bots.BotMovementManager;

import java.util.ArrayList;

public final class AgentBotInventoryRuntimeAdapters {
    private AgentBotInventoryRuntimeAdapters() {
    }

    public static AgentPassiveLootRuntimeService.RuntimeCallbacks passiveLootRuntimeCallbacks() {
        return AgentPassiveLootRuntimeService.RuntimeCallbacks.of(
                AgentBotInventoryStateRuntime::hasLootInhibit,
                entry -> AgentBotInventoryStateRuntime.tickLootInhibit(entry, BotMovementManager::tickDown),
                AgentBotPendingTradeStateRuntime::hasActiveSequence,
                entry -> AgentBotInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, BotMovementManager::tickDown),
                System::currentTimeMillis,
                () -> BotManager.cfg.LOOT_RADIUS,
                AgentBotInventoryStateRuntime::canWarnInventoryFull,
                AgentBotInventoryRuntime::replyNow,
                () -> BotMovementManager.delayAfterCurrentTick(BotManager.cfg.INV_FULL_WARN_CD_MS),
                AgentBotInventoryStateRuntime::setInventoryFullWarnCooldownMs,
                AgentBotRuntimeIdentityRuntime::owner,
                AgentBotOfferStateRuntime::pendingLootOfferItem,
                AgentInventoryItemPolicy::hasItem,
                BotEquipManager::autoEquip,
                AgentOfferService::scheduleLootOfferPrompt,
                AgentLootCleanupService::cleanupGhostDrop,
                Character::pickupItem);
    }

    public static AgentManualTradeRuntimeService.RuntimeCallbacks manualTradeRuntimeCallbacks(BotEntry entry) {
        return AgentManualTradeRuntimeService.RuntimeCallbacks.of(
                () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                BotMovementManager::tickDown,
                BotMovementManager::configuredTickMs,
                peer -> peer.getClient() instanceof client.BotClient,
                (peerId, ownerId) -> AgentOwnershipService.getInstance().isAuthorizedOwner(peerId, ownerId),
                () -> BotManager.getInstance().manualTradeGreeting(),
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null));
    }

    public static AgentTradeTickRuntimeService.RuntimeCallbacks tradeTickRuntimeCallbacks() {
        return AgentTradeTickRuntimeService.RuntimeCallbacks.of(
                BotMovementManager::tickDown,
                Character::getTrade,
                BotMovementManager::delayAfterCurrentTick,
                BotMovementManager::configuredTickMs,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null),
                AgentTradeRecipientService::resolveTradeRecipient,
                recipient -> recipient.getClient() instanceof client.BotClient);
    }

    public static AgentTradeLifecycleRuntimeService.RuntimeCallbacks tradeLifecycleRuntimeCallbacks() {
        return AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                AgentEquippedSlotTradeService::restoreTemporarilyUnequippedItems,
                AgentManualTradeService::clearState,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> BotEquipManager.autoEquip(agent, owner, null),
                BotManager::randMs,
                AgentTradeDialogueService::thanksReply,
                AgentTradeDialogueService::freebieReply);
    }

    public static AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks transferAvailabilityRuntimeCallbacks() {
        return AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                AgentBotRuntimeIdentityRuntime::owner,
                AgentInventoryNamedItemService::countNamedItems,
                (agent, fragment) -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, BotEquipManager::slotsFromName));
    }

    public static AgentInventoryTradeRuntimeService.RuntimeCallbacks tradeRuntimeCallbacks(BotEntry entry, Character agent) {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                (owner, holder) -> new ArrayList<>(BotEquipManager.findRecommendedEquips(owner, holder).stream()
                        .map(AgentEquipRecommendation::candidate)
                        .toList()),
                AgentAttackExecutionProvider::getEquippedWeaponType,
                itemId -> ItemInformationProvider.getInstance().getWatkForProjectile(itemId),
                itemId -> ItemInformationProvider.getInstance().isQuestItem(itemId),
                () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE,
                () -> AgentTradeCommandProfiler.profileCategory("equips"),
                AgentEquipmentReservePolicy::collectPotentialSelfUpgradeItems,
                item -> AgentOfferService.isReservedForOtherRecipients(entry, agent, item),
                () -> AgentBotRuntimeIdentityRuntime.owner(entry));
    }
}
