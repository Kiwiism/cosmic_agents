package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementTimers;

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
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeConfig;
import server.bots.BotEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;

import java.util.ArrayList;

public final class AgentBotInventoryRuntimeAdapters {
    private AgentBotInventoryRuntimeAdapters() {
    }

    public static AgentPassiveLootRuntimeService.RuntimeCallbacks passiveLootRuntimeCallbacks() {
        return AgentPassiveLootRuntimeService.RuntimeCallbacks.of(
                AgentBotInventoryStateRuntime::hasLootInhibit,
                entry -> AgentBotInventoryStateRuntime.tickLootInhibit(entry, AgentMovementTimers::tickDown),
                AgentBotPendingTradeStateRuntime::hasActiveSequence,
                entry -> AgentBotInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, AgentMovementTimers::tickDown),
                System::currentTimeMillis,
                () -> AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentBotInventoryStateRuntime::canWarnInventoryFull,
                AgentBotInventoryRuntime::replyNow,
                () -> AgentMovementTimers.delayAfterCurrentTick(AgentRuntimeConfig.cfg.INV_FULL_WARN_CD_MS),
                AgentBotInventoryStateRuntime::setInventoryFullWarnCooldownMs,
                AgentBotRuntimeIdentityRuntime::owner,
                AgentBotOfferStateRuntime::pendingLootOfferItem,
                AgentInventoryItemPolicy::hasItem,
                AgentEquipmentService::autoEquip,
                (entry, bot, item, delayMs) -> AgentOfferService.scheduleLootOfferPrompt((BotEntry) entry, bot, item, delayMs),
                AgentLootCleanupService::cleanupGhostDrop,
                Character::pickupItem);
    }

    public static AgentManualTradeRuntimeService.RuntimeCallbacks manualTradeRuntimeCallbacks(BotEntry entry) {
        return AgentManualTradeRuntimeService.RuntimeCallbacks.of(
                () -> AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                AgentMovementTimers::tickDown,
                AgentMovementPhysicsConfig::configuredMovementTickMs,
                peer -> peer.getClient() instanceof client.BotClient,
                (peerId, ownerId) -> AgentOwnershipService.getInstance().isAuthorizedOwner(peerId, ownerId),
                AgentTradeDialogueService::manualTradeGreeting,
                (agent, owner) -> AgentEquipmentService.autoEquip(agent, owner, null));
    }

    public static AgentTradeTickRuntimeService.RuntimeCallbacks tradeTickRuntimeCallbacks() {
        return AgentTradeTickRuntimeService.RuntimeCallbacks.of(
                AgentMovementTimers::tickDown,
                Character::getTrade,
                AgentMovementTimers::delayAfterCurrentTick,
                AgentMovementPhysicsConfig::configuredMovementTickMs,
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> AgentEquipmentService.autoEquip(agent, owner, null),
                AgentTradeRecipientService::resolveTradeRecipient,
                recipient -> recipient.getClient() instanceof client.BotClient);
    }

    public static AgentTradeLifecycleRuntimeService.RuntimeCallbacks tradeLifecycleRuntimeCallbacks() {
        return AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                AgentEquippedSlotTradeService::restoreTemporarilyUnequippedItems,
                (entry, agent) -> AgentManualTradeService.clearState((BotEntry) entry, agent),
                AgentBotRuntimeIdentityRuntime::owner,
                (agent, owner) -> AgentEquipmentService.autoEquip(agent, owner, null),
                AgentRandom::randMs,
                AgentTradeDialogueService::thanksReply,
                AgentTradeDialogueService::freebieReply);
    }

    public static AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks transferAvailabilityRuntimeCallbacks() {
        return AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                AgentBotRuntimeIdentityRuntime::owner,
                AgentInventoryNamedItemService::countNamedItems,
                (agent, fragment) -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, AgentEquipmentService::slotsFromName));
    }

    public static AgentInventoryTradeRuntimeService.RuntimeCallbacks tradeRuntimeCallbacks(BotEntry entry, Character agent) {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                (owner, holder) -> new ArrayList<>(AgentEquipmentService.findRecommendedEquips(owner, holder).stream()
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
