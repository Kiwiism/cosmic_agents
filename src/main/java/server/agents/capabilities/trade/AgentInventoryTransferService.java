package server.agents.capabilities.trade;

import server.agents.capabilities.movement.AgentMovementTimers;

import client.Character;
import client.inventory.Item;
import config.YamlConfig;
import server.ItemInformationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.equipment.AgentEquipRecommendation;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquippedSlotTradeService;
import server.agents.capabilities.inventory.AgentInventoryDropService;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent-owned boundary for inventory transfer commands while the deeper trade
 * state machine is still being reconstructed out of temporary bot runtime
 * callbacks.
 */
public final class AgentInventoryTransferService {
    private static final Logger log = LoggerFactory.getLogger(AgentInventoryTransferService.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;

    private AgentInventoryTransferService() {
    }

    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character agent) {
        if (tradeToOwner) {
            startTradeTransfer(category, entry, agent);
            return;
        }
        AgentInventoryDropService.dropCategory(
                category,
                entry,
                agent,
                AgentInventorySellTrashService::collectSellTrashEquips);
        AgentBotInventoryStateRuntime.setLootInhibitMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(20_000));
    }

    public static void startTradeTransfer(String category, BotEntry entry, Character agent) {
        long startedAt = AgentTradeCommandProfiler.profileCategory(category) ? System.nanoTime() : 0L;
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);

        AgentTradeTransferRouter.routeCategoryTransfer(
                category,
                owner != null,
                agent.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                owner != null && owner.getTrade() != null,
                startedAt,
                AgentTradeTransferRouter.TransferCallbacks.of(
                        () -> startTradeMesoTransfer(category, entry, agent),
                        () -> startEquipsGroupTradeTransfer(owner, entry, agent),
                        () -> {
                            List<Item> items = collectReservedEquipTradePage(category, entry, agent);
                            AgentReservedEquipTradeTransferService.startReservedEquipTradeTransfer(
                                    category,
                                    items,
                                    () -> reservedEquipsPageMessage(category, entry, agent),
                                    (reservedCategory, reservedItems) -> startTradeSequence(
                                            reservedCategory, owner, reservedItems, 0, true, entry, agent),
                                    message -> AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, message),
                                    reply -> AgentBotInventoryRuntime.replyNow(entry, reply));
                        },
                        () -> startAmmoGroupTradeTransfer(owner, entry, agent),
                        () -> prepareTradeItems(category, entry, agent),
                        prepared -> AgentPreparedTradeTransferService.startPreparedTradeTransfer(
                                category,
                                prepared,
                                () -> AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry),
                                (preparedCategory, preparedItems, restoreSlots) ->
                                        startTradeSequence(preparedCategory, owner, preparedItems, 0, restoreSlots, entry, agent),
                                reply -> AgentBotInventoryRuntime.replyNow(entry, reply)),
                        reply -> AgentBotInventoryRuntime.replyNow(entry, reply),
                        (operation, operationStartedAt) -> AgentTradeCommandProfiler.logSlowCommand(
                                category,
                                operation,
                                entry,
                                agent,
                                operationStartedAt,
                                TRADE_COMMAND_PROFILE_WARN_NS,
                                log)));
    }

    public static void startTradeTransfer(Item item, Character recipient, AgentRuntimeEntry entry, Character agent) {
        AgentDirectItemTradeService.DirectItemTradeDecision decision = AgentDirectItemTradeService.decideStart(
                recipient != null,
                AgentInventoryItemPolicy.hasItem(agent, item),
                agent.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                recipient != null && recipient.getTrade() != null);
        AgentDirectItemTradeService.routeStart(
                decision,
                () -> startTradeSequence("loot_offer", recipient, List.of(item), 0, true, entry, agent),
                () -> AgentBotPendingTradeStateRuntime.queueRetry(
                        entry,
                        () -> startTradeTransfer(item, recipient, entry, agent),
                        AgentMovementTimers.delayAfterCurrentTick(10_000)),
                reply -> AgentBotInventoryRuntime.replyNow(entry, reply));
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.hasTransferableItems(
                category,
                agent,
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, AgentEquipmentService::slotsFromName),
                () -> collectItems(category, entry, agent));
    }

    public static int countTransferableItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.countTransferableItems(
                category,
                agent,
                fragment -> AgentInventoryNamedItemService.countNamedItems(agent, fragment),
                fragment -> AgentEquippedSlotTradeService.countEquippedSlotItems(agent, fragment, AgentEquipmentService::slotsFromName),
                () -> AgentInventoryTradePolicy.itemQuantitySum(collectItems(category, entry, agent)));
    }

    private static List<Item> collectItems(String category, BotEntry entry, Character agent) {
        return AgentTradeItemCollectionService.collectItems(
                category,
                agent,
                AgentBotRuntimeIdentityRuntime.owner(entry),
                AgentTradeItemCollectionService.TradeItemCollectionCallbacks.of(
                        () -> recommendedItems(entry, agent),
                        () -> classifyEquipTradeGroups(entry, agent),
                        () -> classifyAmmoTradeGroups(agent)));
    }

    private static void startTradeSequence(String category,
                                           Character recipient,
                                           List<Item> items,
                                           int mesos,
                                           boolean singleBatch,
                                           AgentRuntimeEntry entry,
                                           Character agent) {
        AgentTradeSequenceService.startSequence(
                category,
                recipient,
                items,
                mesos,
                singleBatch,
                entry,
                (batchItems, batchMesos) -> openTradeBatch(entry, agent, batchItems, batchMesos));
    }

    private static void openTradeBatch(AgentRuntimeEntry entry, Character agent, List<Item> items, int mesos) {
        AgentTradeBatchService.openBatch(
                entry,
                agent,
                items,
                mesos,
                () -> AgentTradeRecipientService.resolveTradeRecipient(entry, agent),
                () -> AgentTradeCancellationService.cancelSequence(
                        entry,
                        agent,
                        "can't trade right now, stopping",
                        () -> AgentTradeResetService.reset(
                                entry,
                                agent,
                                () -> AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems((BotEntry) entry, agent),
                                () -> AgentManualTradeService.clearState(entry, agent),
                                () -> AgentEquipmentService.autoEquip(agent, AgentBotRuntimeIdentityRuntime.owner(entry), null))),
                () -> server.Trade.startTrade(agent),
                server.Trade::inviteTrade,
                AgentTradeDialogueService::invitationReply,
                message -> AgentBotInventoryRuntime.replyNow(entry, message));
    }

    private static void startTradeMesoTransfer(String category, BotEntry entry, Character agent) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentMesoTradeService.MesoTradeStartDecision decision = AgentMesoTradeService.decideStart(
                category,
                owner != null,
                agent.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry),
                owner != null && owner.getTrade() != null,
                agent.getMeso());
        AgentMesoTradeService.routeStart(
                decision,
                mesos -> startTradeSequence(category, owner, List.of(), mesos, true, entry, agent),
                reply -> AgentBotInventoryRuntime.replyNow(entry, reply));
    }

    private static PreparedTradeItems prepareTradeItems(String category, BotEntry entry, Character agent) {
        return AgentInventoryTradeCollectionService.prepareTradeItems(
                category,
                agent,
                fragment -> {
                    AgentEquippedSlotTradeService.PreparedTradeItems equippedSlotItems =
                            AgentEquippedSlotTradeService.prepareEquippedSlotTradeItems(
                                    fragment,
                                    entry,
                                    agent,
                                    AgentEquipmentService::slotsFromName,
                                    () -> AgentEquippedSlotTradeService.restoreTemporarilyUnequippedItems(entry, agent));
                    return new PreparedTradeItems(equippedSlotItems.items(), equippedSlotItems.errorMessage());
                },
                fragment -> AgentInventoryNamedItemService.collectNamedItems(agent, fragment),
                () -> recommendedItems(entry, agent),
                () -> classifyEquipTradeGroups(entry, agent),
                () -> classifyAmmoTradeGroups(agent),
                AgentBotRuntimeIdentityRuntime.owner(entry));
    }

    private static List<Item> collectReservedEquipTradePage(String category, BotEntry entry, Character agent) {
        return AgentEquipTradeGroupService.reservedEquipTradePage(category, classifyEquipTradeGroups(entry, agent));
    }

    private static String reservedEquipsPageMessage(String category, BotEntry entry, Character agent) {
        return AgentEquipTradeGroupService.reservedEquipsPageMessage(category, classifyEquipTradeGroups(entry, agent));
    }

    public static String equipsGroupMessage(String category) {
        return AgentTradeDialogueService.equipsGroupMessage(category);
    }

    private static void startEquipsGroupTradeTransfer(Character owner, BotEntry entry, Character agent) {
        AgentGroupedTradeTransferService.startEquipsGroupTradeTransfer(
                classifyEquipTradeGroups(entry, agent),
                (category, items) -> startTradeSequence(category, owner, items, 0, false, entry, agent),
                AgentInventoryTransferService::equipsGroupMessage,
                (category, message) -> AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, message),
                reply -> AgentBotInventoryRuntime.replyNow(entry, reply));
    }

    private static void startAmmoGroupTradeTransfer(Character owner, BotEntry entry, Character agent) {
        AgentGroupedTradeTransferService.startAmmoGroupTradeTransfer(
                classifyAmmoTradeGroups(agent),
                (category, items) -> startTradeSequence(category, owner, items, 0, false, entry, agent),
                reply -> AgentBotInventoryRuntime.replyNow(entry, reply));
    }

    private static List<Item> recommendedItems(BotEntry entry, Character agent) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null) {
            return List.of();
        }
        return new ArrayList<>(AgentEquipmentService.findRecommendedEquips(owner, agent).stream()
                .map(AgentEquipRecommendation::candidate)
                .toList());
    }

    private static AgentEquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character agent) {
        return AgentEquipTradeClassificationService.classifyEquipTradeGroups(
                agent,
                AgentEquipTradeClassificationService.ClassificationCallbacks.of(
                        () -> AgentTradeCommandProfiler.profileCategory("equips"),
                        () -> TRADE_COMMAND_PROFILE_WARN_NS,
                        AgentEquipTradeClassificationService.ClassificationCallbacks::collectEquipBag,
                        AgentEquipmentReservePolicy::collectPotentialSelfUpgradeItems,
                        item -> AgentOfferService.isReservedForOtherRecipients(entry, agent, item),
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

    private static AmmoTradeGroups classifyAmmoTradeGroups(Character agent) {
        return AgentAmmoTradeClassificationService.classifyAmmoTradeGroups(
                agent,
                AgentAmmoTradeClassificationService.AmmoTradeCallbacks.of(
                        () -> AgentAttackExecutionProvider.getEquippedWeaponType(agent),
                        ItemInformationProvider.getInstance()::getWatkForProjectile,
                        ItemInformationProvider.getInstance()::isQuestItem,
                        () -> YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE));
    }
}
