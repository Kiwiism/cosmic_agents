package server.agents.integration;

import server.agents.capabilities.trade.AgentOfferStateRuntime;
import server.agents.capabilities.trade.AgentPendingTradeStateRuntime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementTimers;

import client.Character;
import config.YamlConfig;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.capabilities.inventory.AgentInventoryStateRuntime;
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
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.ArrayList;

public final class AgentInventoryRuntimeAdapters {
    private AgentInventoryRuntimeAdapters() {
    }

    public static AgentPassiveLootRuntimeService.RuntimeCallbacks passiveLootRuntimeCallbacks() {
        return AgentPassiveLootRuntimeService.RuntimeCallbacks.of(
                AgentInventoryStateRuntime::hasLootInhibit,
                entry -> AgentInventoryStateRuntime.tickLootInhibit(entry, AgentMovementTimers::tickDown),
                AgentPendingTradeStateRuntime::hasActiveSequence,
                entry -> AgentInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, AgentMovementTimers::tickDown),
                System::currentTimeMillis,
                () -> AgentRuntimeConfig.cfg.LOOT_RADIUS,
                AgentInventoryStateRuntime::canWarnInventoryFull,
                AgentInventoryRuntime::replyNow,
                () -> AgentMovementTimers.delayAfterCurrentTick(AgentRuntimeConfig.cfg.INV_FULL_WARN_CD_MS),
                AgentInventoryStateRuntime::setInventoryFullWarnCooldownMs,
                AgentRuntimeIdentityRuntime::owner,
                AgentOfferStateRuntime::pendingLootOfferItem,
                AgentInventoryItemPolicy::hasItem,
                AgentEquipmentService::autoEquip,
                AgentOfferService::scheduleLootOfferPrompt,
                AgentLootCleanupService::cleanupGhostDrop,
                Character::pickupItem);
    }

    public static AgentManualTradeRuntimeService.RuntimeCallbacks manualTradeRuntimeCallbacks(AgentRuntimeEntry entry) {
        return AgentManualTradeRuntimeService.RuntimeCallbacks.of(
                () -> AgentPendingTradeStateRuntime.hasActiveSequence(entry),
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
                AgentRuntimeIdentityRuntime::owner,
                (agent, owner) -> AgentEquipmentService.autoEquip(agent, owner, null),
                AgentTradeRecipientService::resolveTradeRecipient,
                recipient -> recipient.getClient() instanceof client.BotClient);
    }

    public static AgentTradeLifecycleRuntimeService.RuntimeCallbacks tradeLifecycleRuntimeCallbacks() {
        return AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                AgentEquippedSlotTradeService::restoreTemporarilyUnequippedItems,
                AgentManualTradeService::clearState,
                AgentRuntimeIdentityRuntime::owner,
                (agent, owner) -> AgentEquipmentService.autoEquip(agent, owner, null),
                AgentRandom::randMs,
                AgentTradeDialogueService::thanksReply,
                AgentTradeDialogueService::freebieReply);
    }

    public static AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks transferAvailabilityRuntimeCallbacks() {
        return AgentTradeTransferAvailabilityRuntimeService.RuntimeCallbacks.of(
                AgentRuntimeIdentityRuntime::owner,
                (agent, fragment) -> AgentInventoryNamedItemService.countNamedItems(agent, fragment, inventory()),
                (agent, fragment) -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, AgentEquipmentService::slotsFromName));
    }

    public static AgentInventoryTradeRuntimeService.RuntimeCallbacks tradeRuntimeCallbacks(AgentRuntimeEntry entry, Character agent) {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                (owner, holder) -> new ArrayList<>(AgentEquipmentService.findRecommendedEquips(owner, holder).stream()
                        .map(AgentEquipRecommendation::candidate)
                        .toList()),
                AgentAttackExecutionProvider::getEquippedWeaponType,
                itemId -> inventory().getProjectileWeaponAttack(itemId),
                itemId -> inventory().isQuestItem(itemId),
                () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE,
                () -> AgentTradeCommandProfiler.profileCategory("equips"),
                AgentEquipmentReservePolicy::collectPotentialSelfUpgradeItems,
                item -> AgentOfferService.isReservedForOtherRecipients(entry, agent, item),
                () -> AgentRuntimeIdentityRuntime.owner(entry),
                AgentInventoryRuntimeAdapters::inventory);
    }

    private static InventoryGateway inventory() {
        return CosmicAgentServerAdapter.INSTANCE.inventory();
    }
}
