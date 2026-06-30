package server.bots;

import server.agents.capabilities.navigation.AgentNavigationGraphService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatConfig;

import server.agents.capabilities.dialogue.AgentEmote;

import server.agents.capabilities.looting.AgentLootEligibility;

import client.BotClient;
import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryNamedItemService;
import server.agents.capabilities.inventory.AgentInventorySellTrashService;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.AmmoGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.UseTradeGroups;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.supplies.AgentAmmoService;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentSupplyShareTradeService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.ItemInformationProvider;
import server.Trade;
import server.maps.FieldLimit;
import server.maps.MapItem;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class BotInventoryManager {
    private static final Logger log = LoggerFactory.getLogger(BotInventoryManager.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;
    private static final int MANUAL_TRADE_TIMEOUT_MS = 60_000;
    private record PreparedTradeItems(List<Item> items, String errorMessage) {}
    private record EquipTradeGroups(List<Item> normal,
                                    List<Item> reservedForOther,
                                    List<Item> reservedForSelf) {
        List<Item> itemsFor(EquipsGroup group) {
            return switch (group) {
                case NORMAL -> normal;
                case RESERVED_FOR_OTHER -> reservedForOther;
                case RESERVED_FOR_SELF -> reservedForSelf;
            };
        }
    }
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
                cleanupBotLootGhostDrop(bot, drop);
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
            cleanupBotLootGhostDrop(bot, drop);
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

    /**
     * Returns the nearest lootable drop within GRIND_SEEK_RANGE, with no region
     * restriction. Returns null when any inventory is full or no eligible drop exists.
     */
    static MapItem findNearestGrindLootTarget(BotEntry entry, Character bot) {
        if (bot == null || hasAnyInventoryFull(bot)) return null;
        MapleMap map = bot.getMap();
        if (map == null) return null;

        long now = System.currentTimeMillis();
        Point botPos = bot.getPosition();
        double seekRangeSq = (double) AgentCombatConfig.cfg.GRIND_SEEK_RANGE * AgentCombatConfig.cfg.GRIND_SEEK_RANGE;
        MapItem nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (MapItem drop : map.getDroppedItems()) {
            if (!AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, now)) continue;
            if (BotManager.isGrindLootRetrySuppressed(entry, drop, now)) continue;
            Point dropPos = drop.getPosition();
            if (Math.abs(dropPos.x - botPos.x) <= BotManager.cfg.LOOT_RADIUS
                    && Math.abs(dropPos.y - botPos.y) <= BotManager.cfg.LOOT_RADIUS) {
                continue;
            }
            double distSq = dropPos.distanceSq(botPos);
            if (distSq > seekRangeSq || distSq >= nearestDistSq) continue;
            nearestDistSq = distSq;
            nearest = drop;
        }
        return nearest;
    }

    static boolean hasAnyInventoryFull(Character bot) {
        if (bot == null) return false;
        for (InventoryType type : new InventoryType[]{
                InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC}) {
            Inventory inv = bot.getInventory(type);
            if (inv != null && inv.isFull()) return true;
        }
        return false;
    }

    /**
     * Returns the position of the nearest lootable drop within the patrol region
     * and its immediate neighbours (1 graph hop). Returns null when no eligible
     * drop exists, the graph is unavailable, or any inventory is full.
     */
    static Point findNearestPatrolLootTarget(BotEntry entry, int patrolRegionId) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (bot == null) return null;
        if (hasAnyInventoryFull(bot)) return null;
        MapleMap map = bot.getMap();
        if (map == null) return null;

        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) return null;

        Set<Integer> allowed = new HashSet<>();
        allowed.add(patrolRegionId);
        allowed.addAll(graph.getMutualAdjacentRegionIds(patrolRegionId));

        long now = System.currentTimeMillis();
        Point botPos = bot.getPosition();
        Point nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (MapItem drop : map.getDroppedItems()) {
            if (!AgentLootEligibility.canBotTargetLoot(entry, bot, map, drop, now)) continue;
            Point dropPos = drop.getPosition();
            if (!allowed.contains(graph.findRegionId(map, dropPos))) continue;
            double distSq = dropPos.distanceSq(botPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = dropPos;
            }
        }
        return nearest;
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

    private static void cleanupBotLootGhostDrop(Character bot, MapItem drop) {
        if (drop == null) {
            return;
        }
        if (!drop.isPickedUp() && bot.getMap().getMapObject(drop.getObjectId()) == drop) {
            return;
        }

        Packet removePacket = PacketCreator.removeItemFromMap(drop.getObjectId(), 1, 0);
        for (Character player : bot.getMap().getAllPlayers()) {
            if (player.getClient() instanceof BotClient) {
                continue;
            }
            if (!player.isMapObjectVisible(drop)) {
                continue;
            }
            player.removeVisibleMapObject(drop);
            player.sendPacket(removePacket);
        }
    }

    /**
     * Called after the owner chooses "drop" or "trade" in the item-choice prompt.
     * category: "scrolls", "pots", "equips", "etc", or "name:<fragment>"
     */
    public static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character bot) {
        if (tradeToOwner) {
            startTradeTransfer(category, entry, bot);
        } else {
            dropCategory(category, entry, bot);
            AgentBotInventoryStateRuntime.setLootInhibitMs(
                    entry,
                    BotMovementManager.delayAfterCurrentTick(20_000)); // ~20s: prevents bot re-looting its own floor drops
        }
    }

    private static void dropCategory(String category, BotEntry entry, Character bot) {
        // When items aren't globally tradable, drops on drop-limited maps (e.g. free market)
        // silently vanish. Refuse and tell the owner why instead of destroying the items.
        if (!YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE
                && FieldLimit.DROP_LIMIT.check(bot.getMap().getFieldLimit())) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.dropLimitedMapReply());
            return;
        }
        switch (category) {
            case "scrolls" -> dropScrolls(entry, bot);
            case "pots"    -> dropPotions(entry, bot);
            case "buff"    -> dropBuffPots(entry, bot);
            case "equips"  -> dropEquips(entry, bot);
            case "trash"   -> dropTrashEquips(entry, bot);
            case "etc"     -> dropEtc(entry, bot);
            default -> { if (category.startsWith("name:")) dropByName(entry, bot, category.substring(5)); }
        }
    }

    // ─── Trade actions (actual trade window) ─────────────────────────────────

    /**
     * Kicks off a trade sequence for the given category.
     * Items are batched ≤9 per trade window; subsequent batches open new trades automatically.
     */
    public static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        long startedAt = profileTradeCategory(category) ? System.nanoTime() : 0L;
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
            logSlowTradeCommand(category, "startEquipsGroupTradeTransfer", entry, bot, equipsStartedAt);
            logSlowTradeCommand(category, "startTradeTransfer", entry, bot, startedAt);
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
            logSlowTradeCommand(category, "startTradeTransfer", entry, bot, startedAt);
            return;
        }
        if ("ammo".equals(category)) {
            startAmmoGroupTradeTransfer(owner, entry, bot);
            logSlowTradeCommand(category, "startTradeTransfer", entry, bot, startedAt);
            return;
        }
        long prepareStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        PreparedTradeItems prepared = prepareTradeItems(category, entry, bot);
        logSlowTradeCommand(category, "prepareTradeItems", entry, bot, prepareStartedAt);
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
        logSlowTradeCommand(category, "startTradeTransfer", entry, bot, startedAt);
    }

    public static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        if (recipient == null) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRecipientNotFoundReply());
            return;
        }
        if (!AgentInventoryItemPolicy.hasItem(bot, item)) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeItemMissingReply());
            return;
        }
        if (bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry) || recipient.getTrade() != null) {
            AgentBotPendingTradeStateRuntime.queueRetry(
                    entry,
                    () -> startTradeTransfer(item, recipient, entry, bot),
                    BotMovementManager.delayAfterCurrentTick(10_000));
            return;
        }
        startTradeSequence("loot_offer", recipient, List.of(item), 0, true, entry, bot);
    }

    public static boolean hasTransferableItems(String category, BotEntry entry, Character bot) {
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            int currentMesos = bot.getMeso();
            if (currentMesos <= 0) {
                return false;
            }

            int requestedMesos = AgentInventoryTradePolicy.requestedTradeMesos(category);
            return requestedMesos <= 0 || currentMesos >= requestedMesos;
        }

        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5);
            if (hasEquippedSlotItems(bot, fragment)) {
                return true;
            }
        }

        return !collectItems(category, entry, bot).isEmpty();
    }

    public static boolean profileTradeCategory(String category) {
        return AgentTradeCommandProfiler.profileCategory(category);
    }

    public static void logSlowTradeCommand(String category, String phase, BotEntry entry, Character bot, long startedAt) {
        AgentTradeCommandProfiler.logSlowCommand(category, phase, entry, bot, startedAt, TRADE_COMMAND_PROFILE_WARN_NS, log);
    }

    public static int countTransferableItems(String category, BotEntry entry, Character bot) {
        if (AgentInventoryTradePolicy.isMesoCategory(category)) {
            return bot.getMeso();
        }
        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5);
            int total = AgentInventoryNamedItemService.countNamedItems(bot, fragment);
            short[] slots = BotEquipManager.slotsFromName(fragment);
            if (slots.length > 0) {
                Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
                ItemInformationProvider ii = ItemInformationProvider.getInstance();
                for (short slot : slots) {
                    Item item = equipped.getItem(slot);
                    if (item != null && !ii.isCash(item.getItemId())) {
                        total++;
                    }
                }
            }
            return total;
        }
        return AgentInventoryTradePolicy.itemQuantitySum(collectItems(category, entry, bot));
    }

    /** Opens a trade for the first ≤9 items; remaining items are re-collected next batch. */
    private static void startTradeSequence(String category,
                                           Character recipient,
                                           List<Item> items,
                                           int mesos,
                                           boolean singleBatch,
                                           BotEntry entry,
                                           Character bot) {
        if (recipient == null) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRecipientNotFoundReply());
            return;
        }
        AgentBotPendingTradeStateRuntime.setCategory(entry, category);
        AgentBotPendingTradeStateRuntime.setRecipientId(entry, recipient.getId());
        AgentBotPendingTradeStateRuntime.setSingleBatch(entry, singleBatch);
        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);
        openTradeBatch(entry, bot, items, mesos);
    }

    private static void openTradeBatch(BotEntry entry, Character bot, List<Item> items, int mesos) {
        Character recipient = resolveTradeRecipient(entry, bot);
        if (recipient == null || recipient.getTrade() != null) {
            cancelTradeSequence(entry, bot, "can't trade right now, stopping");
            return;
        }
        AgentBotPendingTradeStateRuntime.setItems(entry, items.size() > AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT
                ? new ArrayList<>(items.subList(0, AgentInventoryTradePolicy.TRADE_WINDOW_ITEM_LIMIT))
                : new ArrayList<>(items));
        AgentBotPendingTradeStateRuntime.setMeso(entry, mesos);
        AgentBotPendingTradeStateRuntime.clearItemIndex(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
        Trade.startTrade(bot);
        Trade.inviteTrade(bot, recipient);
        // pot_share already announced itself ("got some HP pots, inv u") — skip the redundant "k i inv"
        if (!AgentBotPendingTradeStateRuntime.inviteAnnounced(entry)
                && !AgentBotPendingTradeStateRuntime.isSupplyShareCategory(entry)) {
            AgentBotPendingTradeStateRuntime.markInviteAnnounced(entry);
            AgentBotInventoryRuntime.replyNow(entry, BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()));
        }
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
            if (AgentBotPendingTradeStateRuntime.botDone(entry)) {
                // Both sides confirmed — sequence complete or cancelled after bot OK
                if (AgentBotPendingTradeStateRuntime.singleBatch(entry)) {
                    resetTradeState(entry, bot);
                    BotEquipManager.autoEquip(bot, AgentBotRuntimeIdentityRuntime.owner(entry), null);
                    return;
                }
                AgentBotPendingTradeStateRuntime.clearItems(entry);
                AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
                AgentBotPendingTradeStateRuntime.clearBotDone(entry);
                AgentBotPendingTradeStateRuntime.setTimerMs(entry, BotMovementManager.delayAfterCurrentTick(1_000));
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
            AgentBotPendingTradeStateRuntime.addTimerMs(entry, BotMovementManager.cfg.TICK_MS);
            if (AgentBotPendingTradeStateRuntime.timerMs(entry) > 30_000) {
                AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRequestTimeoutReply());
                Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
                resetTradeState(entry, bot);
            }
            return;
        }

        // ── ADDING ITEMS ──────────────────────────────────────────────────
        if (!AgentBotPendingTradeStateRuntime.allItemsAdded(entry)) {
            if (AgentBotPendingTradeStateRuntime.timerMs(entry) > 0) {
                AgentBotPendingTradeStateRuntime.tickTimerDown(entry, BotMovementManager::tickDown);
                return;
            }

            if (AgentBotPendingTradeStateRuntime.hasMesoToAdd(entry)) {
                if (bot.getMeso() < AgentBotPendingTradeStateRuntime.meso(entry)) {
                    cancelTradeSequence(entry, bot, "don't have that many mesos anymore");
                    return;
                }

                trade.setMeso(AgentBotPendingTradeStateRuntime.meso(entry));
                AgentBotPendingTradeStateRuntime.markMesoAdded(entry);
                AgentBotPendingTradeStateRuntime.setTimerMs(entry, BotMovementManager.delayAfterCurrentTick(500));
                return;
            }

            List<Item> items = AgentBotPendingTradeStateRuntime.items(entry);
            int idx = AgentBotPendingTradeStateRuntime.itemIndex(entry);

            if (idx >= items.size()) {
                // All items added — say so in trade chat and wait for owner OK
                AgentBotPendingTradeStateRuntime.markAllItemsAdded(entry);
                AgentBotPendingTradeStateRuntime.clearTimer(entry);
                String msg = BotManager.randomReply(AgentDialogueCatalog.tradeAllDoneReplies());
                trade.chat(msg);
                return;
            }

            // Send group announcement before the first item
            if (idx == 0 && AgentBotPendingTradeStateRuntime.categoryMessage(entry) != null) {
                trade.chat(AgentBotPendingTradeStateRuntime.takeCategoryMessage(entry));
                AgentBotPendingTradeStateRuntime.setTimerMs(entry, BotMovementManager.delayAfterCurrentTick(600));
                return;
            }

            // Add next item
            Item item = items.get(idx);
            AgentBotPendingTradeStateRuntime.incrementItemIndex(entry);
            AgentBotPendingTradeStateRuntime.setTimerMs(entry, BotMovementManager.delayAfterCurrentTick(500)); // 500 ms before next

            short tradeQty = AgentBotPendingTradeStateRuntime.capShareQuantity(entry, item.getQuantity());

            InventoryType invType = item.getInventoryType();
            Inventory inv = bot.getInventory(invType);
            inv.lockInventory();
            try {
                Item current  = inv.getItem(item.getPosition());
                if (current == null || current != item) return; // slot changed, skip

                Item tradeItem = item.copy();
                tradeItem.setPosition((short) (idx + 1)); // trade-window slot 1-9
                tradeItem.setQuantity(tradeQty);

                if (trade.addItem(tradeItem)) {
                    AgentBotPendingTradeStateRuntime.transferRestoreSlot(entry, item, tradeItem);
                    InventoryManipulator.removeFromSlot(bot.getClient(),
                            invType, item.getPosition(), tradeQty, false);
                    bot.sendPacket(PacketCreator.getTradeItemAdd((byte) 0, tradeItem));
                    if (trade.getPartner() != null) {
                        trade.getPartner().getChr().sendPacket(PacketCreator.getTradeItemAdd((byte) 1, tradeItem));
                    }
                }
            } finally {
                inv.unlockInventory();
            }
            return;
        }

        // ── WAITING FOR OWNER TO CLICK OK ─────────────────────────────────
        if (!AgentBotPendingTradeStateRuntime.botDone(entry)) {
            AgentBotPendingTradeStateRuntime.addTimerMs(entry, BotMovementManager.cfg.TICK_MS);
            Character recipient = resolveTradeRecipient(entry, bot);
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
        AgentBotInventoryRuntime.replyNow(entry, msg);
        if (bot.getTrade() != null) Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
        resetTradeState(entry, bot);
    }

    private static void clearManualTradeState(BotEntry entry, Character bot) {
        manualTradeGreetingSent.remove(bot.getId());
        AgentBotManualTradeStateRuntime.clear(entry);
    }

    private static void resetTradeState(BotEntry entry, Character bot) {
        boolean hadRestores = AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry);
        restoreTemporarilyUnequippedItems(entry, bot);
        clearManualTradeState(entry, bot);
        AgentBotPendingTradeStateRuntime.clearCategory(entry);
        AgentBotPendingTradeStateRuntime.clearCategoryMessage(entry);
        AgentBotPendingTradeStateRuntime.clearItems(entry);
        AgentBotPendingTradeStateRuntime.clearRecipientId(entry);
        AgentBotPendingTradeStateRuntime.clearMeso(entry);
        AgentBotPendingTradeStateRuntime.clearItemIndex(entry);
        AgentBotPendingTradeStateRuntime.clearTimer(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.clearAllItemsAdded(entry);
        AgentBotPendingTradeStateRuntime.clearBotDone(entry);
        AgentBotPendingTradeStateRuntime.clearSingleBatch(entry);
        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);
        AgentBotPendingTradeStateRuntime.clearShareBudget(entry);
        AgentBotPendingTradeStateRuntime.clearOwnerGivenItems(entry);
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
        // Snapshot equips the owner is giving us before the trade clears their side.
        // Trade reuses the same item objects, so identity comparison in ownerGivenItems works.
        if (trade.getPartner() != null) {
            for (Item item : trade.getPartner().getItems()) {
                if (ItemConstants.getInventoryType(item.getItemId()) == InventoryType.EQUIP) {
                    AgentBotPendingTradeStateRuntime.addOwnerGivenItem(entry, item);
                }
            }
        }
        boolean receivedSomething = trade.getPartner() != null && trade.getPartner().hasAnyOffer();
        Trade.completeTrade(bot);
        long replyDelay = BotManager.randMs(800, 1300);
        if (receivedSomething) {
            bot.changeFaceExpression(AgentEmote.HAPPY.getValue());
            AgentBotInventoryRuntime.afterDelay(replyDelay, () ->
                    AgentBotInventoryRuntime.visibleSayNow(entry, BotManager.randomReply(AgentDialogueCatalog.tradeThanksReplies())));
        } else if (ThreadLocalRandom.current().nextInt(100) < 20) {
            bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? AgentEmote.GLARE.getValue() : AgentEmote.ANNOYED.getValue());
            AgentBotInventoryRuntime.afterDelay(replyDelay, () ->
                    AgentBotInventoryRuntime.visibleSayNow(entry, BotManager.randomReply(AgentDialogueCatalog.tradeFreebieReplies())));
        }
    }

    private static void startTradeMesoTransfer(String category, BotEntry entry, Character bot) {
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

        int currentMesos = bot.getMeso();
        if (currentMesos <= 0) {
            AgentBotInventoryRuntime.replyNow(entry, AgentInventoryDialogueReporter.noItemsReply(category));
            return;
        }

        int requestedMesos = AgentInventoryTradePolicy.requestedTradeMesos(category);
        if (requestedMesos == 0) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeMesoInvalidReply());
            return;
        }
        if (requestedMesos > 0 && currentMesos < requestedMesos) {
            AgentBotInventoryRuntime.replyNow(entry, AgentInventoryTradePolicy.notEnoughMesosReply(requestedMesos, currentMesos));
            return;
        }

        startTradeSequence(category, owner, List.of(), requestedMesos > 0 ? requestedMesos : currentMesos, true, entry, bot);
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static PreparedTradeItems prepareTradeItems(String category, BotEntry entry, Character bot) {
        if (category != null && category.startsWith("name:")) {
            String fragment = category.substring(5).trim();
            PreparedTradeItems equippedSlotItems = prepareEquippedSlotTradeItems(fragment, entry, bot);
            if (equippedSlotItems.errorMessage() != null || !equippedSlotItems.items().isEmpty()) {
                return equippedSlotItems;
            }
            return new PreparedTradeItems(AgentInventoryNamedItemService.collectNamedItems(bot, fragment), null);
        }

        return new PreparedTradeItems(collectItems(category, entry, bot), null);
    }

    private static boolean hasEquippedSlotItems(Character bot, String fragment) {
        short[] slots = BotEquipManager.slotsFromName(fragment);
        if (slots.length == 0) {
            return false;
        }

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        for (short slot : slots) {
            Item item = equipped.getItem(slot);
            if (item != null && !ii.isCash(item.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static PreparedTradeItems prepareEquippedSlotTradeItems(String fragment, BotEntry entry, Character bot) {
        short[] slots = BotEquipManager.slotsFromName(fragment);
        if (slots.length == 0) {
            return new PreparedTradeItems(List.of(), null);
        }

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        Inventory equipBag = bot.getInventory(InventoryType.EQUIP);
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        List<Short> occupiedSlots = new ArrayList<>();
        for (short slot : slots) {
            Item item = equipped.getItem(slot);
            if (item != null && !ii.isCash(item.getItemId())) {
                occupiedSlots.add(slot);
            }
        }
        if (occupiedSlots.isEmpty()) {
            return new PreparedTradeItems(List.of(), null);
        }
        if (equipBag.getNumFreeSlot() < occupiedSlots.size()) {
            return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquipBagFullReply());
        }

        occupiedSlots.sort(Short::compare);
        List<Item> result = new ArrayList<>();
        for (short srcSlot : occupiedSlots) {
            short dstSlot = equipBag.getNextFreeSlot();
            if (dstSlot < 0) {
                restoreTemporarilyUnequippedItems(entry, bot);
                return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquipSlotsFullReply());
            }

            InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, srcSlot, dstSlot, (short) 1);
            Item moved = equipBag.getItem(dstSlot);
            if (moved == null) {
                restoreTemporarilyUnequippedItems(entry, bot);
                return new PreparedTradeItems(List.of(), AgentDialogueCatalog.tradeEquippedItemPrepareFailedReply());
            }

            AgentBotPendingTradeStateRuntime.rememberRestoreSlot(entry, moved, srcSlot);
            result.add(moved);
        }

        return new PreparedTradeItems(result, null);
    }

    private static void restoreTemporarilyUnequippedItems(BotEntry entry, Character bot) {
        if (bot == null || !AgentBotPendingTradeStateRuntime.hasRestoreSlots(entry)) {
            AgentBotPendingTradeStateRuntime.clearRestoreSlots(entry);
            return;
        }

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        List<Map.Entry<Item, Short>> restoreEntries = AgentBotPendingTradeStateRuntime.restoreSlotEntries(entry);
        restoreEntries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<Item, Short> restoreEntry : restoreEntries) {
            Item item = restoreEntry.getKey();
            short dstSlot = restoreEntry.getValue();
            if (!AgentInventoryItemPolicy.hasItem(bot, item) || equipped.getItem(dstSlot) != null) {
                continue;
            }
            InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, item.getPosition(), dstSlot, (short) 1);
        }
        AgentBotPendingTradeStateRuntime.clearRestoreSlots(entry);
    }

    private static List<Item> collectItems(String category, BotEntry entry, Character bot) {
        List<Item> result = new ArrayList<>();
        switch (category) {
            case "recommended" -> {
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                if (owner != null) {
                    result.addAll(BotEquipManager.collectRecommendedItems(owner, bot));
                }
            }
            case "scrolls" -> {
                collectFromBag(bot, result, InventoryType.USE,
                        item -> ItemConstants.isEquipScroll(item.getItemId()));
                result = AgentInventoryTradePolicy.prioritizeScrollTradeItems(result, AgentBotRuntimeIdentityRuntime.owner(entry));
            }
            case "pots"    -> collectFromBag(bot, result, InventoryType.USE,
                    item -> AgentUseItemClassificationPolicy.isRecoveryPotion(item.getItemId()));
            case "buff"    -> collectFromBag(bot, result, InventoryType.USE,
                    item -> AgentUseItemClassificationPolicy.isBuffConsumable(item.getItemId()));
            case "use"     -> {
                UseTradeGroups groups = classifyUseTradeGroups(bot, AgentBotRuntimeIdentityRuntime.owner(entry));
                result.addAll(groups.uncategorized());
                result.addAll(groups.categorized());
            }
            case "ammo" -> {
                AmmoTradeGroups groups = classifyAmmoTradeGroups(bot);
                result.addAll(groups.nonOwn());
                result.addAll(groups.own());
            }
            case "equips" -> {
                EquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
                for (EquipsGroup g : EquipsGroup.values()) result.addAll(groups.itemsFor(g));
            }
            case "trash" -> result.addAll(collectTrashEquips(entry, bot));
            case "etc" -> {
                collectFromBag(bot, result, InventoryType.ETC, item -> true);
                result = AgentInventoryTradePolicy.prioritizeEtcTradeItems(result, AgentBotRuntimeIdentityRuntime.owner(entry));
            }
            default -> {
                if (AgentInventoryTradePolicy.isReservedEquipsCategory(category)) {
                    result.addAll(collectReservedEquipTradePage(category, entry, bot));
                } else {
                    EquipsGroup eg = AgentInventoryTradePolicy.equipsGroupFromCategory(category);
                    if (eg != null) {
                        result.addAll(classifyEquipTradeGroups(entry, bot).itemsFor(eg));
                    } else {
                        AmmoGroup ammoGroup = AgentInventoryTradePolicy.ammoGroupFromCategory(category);
                        if (ammoGroup != null) {
                            result.addAll(classifyAmmoTradeGroups(bot).itemsFor(ammoGroup));
                        } else if (category.startsWith("name:")) {
                            result.addAll(AgentInventoryNamedItemService.collectNamedItems(bot, category.substring(5)));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void collectFromBag(Character bot, List<Item> result,
                                       InventoryType type, Predicate<Item> filter) {
        result.addAll(AgentInventoryItemPolicy.collectSafeItems(bot, type, filter,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE));
    }

    private static Character resolveTradeRecipient(BotEntry entry, Character bot) {
        int recipientId = AgentBotPendingTradeStateRuntime.recipientId(entry);
        if (recipientId <= 0) {
            return AgentBotRuntimeIdentityRuntime.owner(entry);
        }

        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner != null && owner.getId() == recipientId) {
            return owner;
        }

        if (bot.getMap() != null) {
            Character mapRecipient = bot.getMap().getCharacterById(recipientId);
            if (mapRecipient != null) {
                return mapRecipient;
            }
        }

        if (owner == null || owner.getParty() == null) {
            return null;
        }

        for (Character member : owner.getPartyMembersOnline()) {
            if (member != null && member.getId() == recipientId) {
                return member;
            }
        }

        return null;
    }

    // ─── Drop actions (floor) ─────────────────────────────────────────────────

    static void dropScrolls(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.USE,
                item -> ItemConstants.isEquipScroll(item.getItemId()));
        reply(entry, bot, count, "scroll");
    }

    static void dropPotions(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.USE,
                item -> AgentUseItemClassificationPolicy.isRecoveryPotion(item.getItemId()));
        reply(entry, bot, count, "potion");
    }

    static void dropEquips(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.EQUIP, item -> true);
        AgentBotInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " equip" + (count != 1 ? "s" : "") + "!"
                          : "equip bag is already empty");
    }

    static void dropTrashEquips(BotEntry entry, Character bot) {
        Set<Item> trash = new java.util.HashSet<>(collectTrashEquips(entry, bot));
        int count = dropFromBag(bot, InventoryType.EQUIP, trash::contains);
        AgentBotInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " trash equip" + (count != 1 ? "s" : "") + "!"
                          : "no trash equips to drop");
    }

    static void dropBuffPots(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.USE,
                item -> AgentUseItemClassificationPolicy.isBuffConsumable(item.getItemId()));
        reply(entry, bot, count, "buff pot");
    }

    static void dropEtc(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.ETC, item -> true);
        reply(entry, bot, count, "etc item");
    }

    static void dropByName(BotEntry entry, Character bot, String nameFragment) {
        String normalizedFragment = AgentInventoryNamedItemService.normalizeQuery(nameFragment);
        int total = 0;
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            total += dropFromBag(bot, type,
                    item -> AgentInventoryNamedItemService.itemNameContains(item.getItemId(), normalizedFragment));
        }
        if (total <= 0) {
            AgentBotInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeNamedItemNotFoundReply(nameFragment));
        }
    }

    // ─── Inventory info ───────────────────────────────────────────────────────

    /** occupied/total for each bag: "equip: 10/24, use: 8/24, etc: 3/24, setup: 0/24" */
    static String slotsReport(Character bot) {
        return AgentInventoryDialogueReporter.slotsReport(bot);
    }

    /** Full bag summary: "equip 10/24 | use 8/24 (3 scrolls, 5 pots, 2 buffs) | etc 3/24" */
    static String inventorySummary(Character bot) {
        return AgentInventoryDialogueReporter.inventorySummary(bot);
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private static List<Item> collectReservedEquips(EquipTradeGroups groups) {
        return new ArrayList<>(groups.reservedForSelf());
    }

    private static List<Item> collectReservedEquipTradePage(String category, BotEntry entry, Character bot) {
        EquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
        return AgentInventoryTradePolicy.reservedEquipsPageItems(category, collectReservedEquips(groups));
    }

    private static String reservedEquipsPageMessage(String category, BotEntry entry, Character bot) {
        EquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
        List<Item> reserved = collectReservedEquips(groups);
        return AgentInventoryTradePolicy.reservedEquipsPageMessage(category, reserved.size());
    }

    private static String equipsGroupMsg(String category) {
        EquipsGroup group = AgentInventoryTradePolicy.equipsGroupFromCategory(category);
        if (group == null) return null;
        return switch (group) {
            case RESERVED_FOR_OTHER -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies());
            case RESERVED_FOR_SELF  -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies());
            default -> null;
        };
    }

    private static String nextEquipsGroup(String category, BotEntry entry, Character bot) {
        EquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
        return AgentInventoryTradePolicy.nextAvailableEquipsGroupCategory(category,
                group -> !groups.itemsFor(group).isEmpty());
    }

    private static String nextAmmoGroup(String category, Character bot) {
        return AgentInventoryAmmoPolicy.nextAvailableGroupCategory(category, classifyAmmoTradeGroups(bot));
    }

    private static void startEquipsGroupTradeTransfer(Character owner, BotEntry entry, Character bot) {
        EquipTradeGroups groups = classifyEquipTradeGroups(entry, bot);
        EquipsGroup group = AgentInventoryTradePolicy.firstAvailableEquipsGroup(
                candidate -> !groups.itemsFor(candidate).isEmpty());
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

    private static UseTradeGroups classifyUseTradeGroups(Character bot, Character recipient) {
        return AgentInventoryTradePolicy.classifyUseTradeGroups(bot, recipient,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentInventoryAmmoPolicy::isTradeAmmoItem,
                ItemConstants::isEquipScroll,
                AgentUseItemClassificationPolicy::isBuffConsumable,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
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

    public static List<Item> collectSellTrashEquips(BotEntry entry, Character bot) {
        return AgentInventorySellTrashService.collectSellTrashEquips(entry, bot);
    }

    private static EquipTradeGroups classifyEquipTradeGroups(BotEntry entry, Character bot) {
        long startedAt = profileTradeCategory("equips") ? System.nanoTime() : 0L;
        long bagScanStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        List<Item> all = new ArrayList<>();
        collectFromBag(bot, all, InventoryType.EQUIP, item -> true);
        long bagScanNs = startedAt != 0L ? System.nanoTime() - bagScanStartedAt : 0L;
        long selfKeepStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        Set<Item> selfKeep = BotEquipManager.collectPotentialSelfUpgradeItems(bot);
        long selfKeepNs = startedAt != 0L ? System.nanoTime() - selfKeepStartedAt : 0L;

        List<Item> normal = new ArrayList<>();
        List<Item> reservedForOther = new ArrayList<>();
        List<Item> reservedForSelf = new ArrayList<>();
        long reservedOtherNs = 0L;
        int reservedOtherChecks = 0;
        int reservedOtherHits = 0;
        for (Item item : all) {
            if (selfKeep.contains(item)) {
                reservedForSelf.add(item);
                continue;
            }
            long reservedOtherStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
            boolean isOther = AgentOfferService.isReservedForOtherRecipients(entry, bot, item);
            if (startedAt != 0L) {
                reservedOtherNs += System.nanoTime() - reservedOtherStartedAt;
                reservedOtherChecks++;
                if (isOther) {
                    reservedOtherHits++;
                }
            }
            if (isOther) {
                reservedForOther.add(item);
            } else {
                normal.add(item);
            }
        }

        long sortStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        List<Item> normalSorted = AgentInventoryTradePolicy.sortEquipsByItemId(normal);
        List<Item> reservedForOtherSorted = AgentInventoryTradePolicy.sortEquipsByItemId(reservedForOther);
        List<Item> reservedForSelfSorted = AgentInventoryTradePolicy.sortReservedEquipsByTradeScore(reservedForSelf, bot);
        long sortNs = startedAt != 0L ? System.nanoTime() - sortStartedAt : 0L;
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
                        normalSorted.size(),
                        reservedForOtherSorted.size(),
                        reservedForSelfSorted.size(),
                        String.format("%.1f", bagScanNs / 1_000_000.0),
                        String.format("%.1f", selfKeepNs / 1_000_000.0),
                        String.format("%.1f", reservedOtherNs / 1_000_000.0),
                        reservedOtherChecks,
                        reservedOtherHits,
                        String.format("%.1f", sortNs / 1_000_000.0));
            }
        }
        return new EquipTradeGroups(normalSorted, reservedForOtherSorted, reservedForSelfSorted);
    }

    private static boolean isOwnClassEquip(Character bot, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.isOwnClassEquip(bot, ii, equip);
    }

    private static int dropFromBag(Character bot, InventoryType type, Predicate<Item> filter) {
        Inventory inv = bot.getInventory(type);
        List<Short> slots = AgentInventoryItemPolicy.selectSafeDropSlots(bot, type, filter,
                ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
        int count = 0;
        for (short slot : slots) {
            Item item = inv.getItem(slot);
            if (item == null) continue;
            InventoryManipulator.drop(bot.getClient(), type, slot, item.getQuantity());
            count++;
        }
        return count;
    }

    static boolean isSafeToDrop(Item item) {
        return AgentInventoryItemPolicy.isSafeToDrop(item, ItemInformationProvider.getInstance()::isQuestItem,
                YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE);
    }

    private static void reply(BotEntry entry, Character bot, int count, String noun) {
        AgentBotInventoryRuntime.replyNow(entry,
                count > 0 ? "dropped " + count + " " + noun + (count != 1 ? "s" : "") + "!"
                          : "no " + noun + "s to drop");
    }

    // ─── Pot-share helpers ────────────────────────────────────────────────────

    /**
     * Collects the donor bot's worst recovery pots (sorted ascending by recovery score)
     * up to {@code maxQty} total quantity or 9 item stacks, whichever limit is reached first.
     * Only pure recovery pots are included (buff pots excluded via isRecoveryPotion).
     */
    public static List<Item> collectPotShareItems(Character donorBot, boolean forHp, int maxQty) {
        return AgentPotionService.collectPotShareItems(donorBot, forHp, maxQty);
    }

    /** Initiates a bot-to-bot pot-share trade (single batch; donor auto-confirms). */
    public static void startPotShareTransfer(List<Item> items, Character recipient, BotEntry entry, Character bot, int maxQty) {
        AgentSupplyShareTradeService.startPotShareTransfer(items, recipient, entry, bot, maxQty);
    }

    public static List<Item> collectAmmoShareItems(Character donorBot, WeaponType needyWeaponType, int maxQty) {
        return AgentAmmoService.collectAmmoShareItems(donorBot, needyWeaponType, maxQty);
    }

    public static void startAmmoShareTransfer(List<Item> items, Character recipient, BotEntry entry, Character bot, int maxQty) {
        AgentSupplyShareTradeService.startAmmoShareTransfer(items, recipient, entry, bot, maxQty);
    }

}
