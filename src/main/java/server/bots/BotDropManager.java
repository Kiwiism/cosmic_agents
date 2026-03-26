package server.bots;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;
import server.Trade;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

class BotDropManager {
    private static final Set<Integer> manualTradeGreetingSent = ConcurrentHashMap.newKeySet();
    private static final List<String> TRADE_THANKS = List.of(
            "ty!", "thanks!", "thank you!", "tyty", "appreciate it!", "tysm!");

    static void tickManualTrade(BotEntry entry, Character bot) {
        if (entry.pendingTradeCategory != null) return;

        Trade trade = bot.getTrade();
        Character owner = entry.owner;
        if (trade == null || owner == null) {
            manualTradeGreetingSent.remove(bot.getId());
            return;
        }

        Trade ownerTrade = owner.getTrade();
        Trade partner = trade.getPartner();
        boolean isOwnerTrade = ownerTrade != null
                && partner == ownerTrade
                && ownerTrade.getPartner() == trade
                && owner.getId() == ownerTrade.getChr().getId();
        if (!isOwnerTrade) {
            manualTradeGreetingSent.remove(bot.getId());
            return;
        }

        if (!trade.isFullTrade()) {
            Trade.visitTrade(bot, owner);
            trade = bot.getTrade();
            if (trade == null || !trade.isFullTrade()) return;
        }

        if (manualTradeGreetingSent.add(bot.getId())) {
            trade.chat(BotManager.getInstance().manualTradeGreeting());
        }

        if (trade.isPartnerConfirmed()) {
            completeTradeAndThank(bot, trade);
        }
    }

    // ─── Entry point from chat choice ─────────────────────────────────────────

    /**
     * Called after the owner chooses "drop" or "trade" in the item-choice prompt.
     * category: "scrolls", "pots", "equips", "etc", or "name:<fragment>"
     */
    static void executeChoice(String category, boolean tradeToOwner, BotEntry entry, Character bot) {
        if (tradeToOwner) {
            startTradeTransfer(category, entry, bot);
        } else {
            dropCategory(category, entry, bot);
            entry.lootInhibitMs = BotMovementManager.delayAfterCurrentTick(20_000); // ~20s: prevents bot re-looting its own floor drops
        }
    }

    private static void dropCategory(String category, BotEntry entry, Character bot) {
        switch (category) {
            case "scrolls" -> dropScrolls(entry, bot);
            case "pots"    -> dropPotions(entry, bot);
            case "equips"  -> dropEquips(entry, bot);
            case "etc"     -> dropEtc(entry, bot);
            default -> { if (category.startsWith("name:")) dropByName(entry, bot, category.substring(5)); }
        }
    }

    // ─── Trade actions (actual trade window) ─────────────────────────────────

    private static final String[] ALL_DONE_MSGS = {
        "that's all!", "done adding items!", "all in!", "everything's added!"
    };

    /**
     * Kicks off a trade sequence for the given category.
     * Items are batched ≤9 per trade window; subsequent batches open new trades automatically.
     */
    static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            BotManager.getInstance().botSay(bot, "can't find you to trade!");
            return;
        }
        if (bot.getTrade() != null || entry.pendingTradeCategory != null) {
            BotManager.getInstance().botSay(bot, "already in a trade!");
            return;
        }
        if (owner.getTrade() != null) {
            BotManager.getInstance().botSay(bot, "you're already in a trade!");
            return;
        }
        List<Item> items = collectItems(category, bot);
        if (items.isEmpty()) {
            BotManager.getInstance().botSay(bot, "nothing to trade!");
            return;
        }
        entry.pendingTradeCategory = category;
        openNextBatch(entry, bot, items);
    }

    /** Opens a trade for the first ≤9 items; remaining items are re-collected next batch. */
    private static void openNextBatch(BotEntry entry, Character bot, List<Item> items) {
        Character owner = entry.owner;
        if (owner == null || owner.getTrade() != null) {
            cancelTradeSequence(entry, bot, "you moved or are already in a trade, stopping");
            return;
        }
        entry.pendingTradeItems    = items.size() > 9 ? new ArrayList<>(items.subList(0, 9)) : items;
        entry.pendingTradeIdx      = 0;
        entry.pendingTradeTimerMs  = 0;
        entry.pendingTradeAllAdded = false;
        entry.pendingTradeBotDone  = false;
        Trade.startTrade(bot);
        Trade.inviteTrade(bot, owner);
        BotManager.getInstance().botSay(bot, "trade request sent!");
    }

    /** Called every bot simulation tick while a trade sequence is in progress. */
    static void tickTrade(BotEntry entry, Character bot) {
        if (entry.pendingTradeCategory == null) return;

        Trade trade = bot.getTrade();

        // ── PAUSE between batches (items == null) ──────────────────────────
        if (entry.pendingTradeItems == null) {
            if (entry.pendingTradeTimerMs > 0) {
                entry.pendingTradeTimerMs = BotMovementManager.tickDown(entry.pendingTradeTimerMs);
                return;
            }
            // Start next batch
            List<Item> next = collectItems(entry.pendingTradeCategory, bot);
            if (next.isEmpty()) {
                resetTradeState(entry);
            } else {
                openNextBatch(entry, bot, next);
            }
            return;
        }

        // ── Trade was closed externally ────────────────────────────────────
        if (trade == null) {
            if (entry.pendingTradeBotDone) {
                // Both sides confirmed — sequence complete or cancelled after bot OK
                entry.pendingTradeItems    = null;
                entry.pendingTradeAllAdded = false;
                entry.pendingTradeBotDone  = false;
                entry.pendingTradeTimerMs  = BotMovementManager.delayAfterCurrentTick(1_000); // 1 s pause then try next batch
            } else if (entry.pendingTradeAllAdded) {
                // Owner cancelled after items were added (items returned to bot)
                BotManager.getInstance().botSay(bot, "trade cancelled");
                resetTradeState(entry);
            } else {
                // Owner declined invite
                BotManager.getInstance().botSay(bot, "trade declined");
                resetTradeState(entry);
            }
            return;
        }

        // ── WAITING FOR ACCEPT ────────────────────────────────────────────
        if (!trade.isFullTrade()) {
            entry.pendingTradeTimerMs += BotMovementManager.cfg.TICK_MS;
            if (entry.pendingTradeTimerMs > 30_000) {
                BotManager.getInstance().botSay(bot, "trade request timed out");
                Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
                resetTradeState(entry);
            }
            return;
        }

        // ── ADDING ITEMS ──────────────────────────────────────────────────
        if (!entry.pendingTradeAllAdded) {
            if (entry.pendingTradeTimerMs > 0) {
                entry.pendingTradeTimerMs = BotMovementManager.tickDown(entry.pendingTradeTimerMs);
                return;
            }

            List<Item> items = entry.pendingTradeItems;
            int idx = entry.pendingTradeIdx;

            if (idx >= items.size()) {
                // All items added — say so in trade chat and wait for owner OK
                entry.pendingTradeAllAdded = true;
                entry.pendingTradeTimerMs  = 0;
                String msg = ALL_DONE_MSGS[ThreadLocalRandom.current().nextInt(ALL_DONE_MSGS.length)];
                trade.chat(msg);
                return;
            }

            // Add next item
            Item item = items.get(idx);
            entry.pendingTradeIdx++;
            entry.pendingTradeTimerMs = BotMovementManager.delayAfterCurrentTick(500); // 500 ms before next

            InventoryType invType = item.getInventoryType();
            Inventory inv = bot.getInventory(invType);
            Item current  = inv.getItem(item.getPosition());
            if (current == null || current != item) return; // slot changed, skip

            Item tradeItem = item.copy();
            tradeItem.setPosition((short) (idx + 1)); // trade-window slot 1-9
            tradeItem.setQuantity(item.getQuantity());

            if (trade.addItem(tradeItem)) {
                InventoryManipulator.removeFromSlot(bot.getClient(),
                        invType, item.getPosition(), item.getQuantity(), false);
                bot.sendPacket(PacketCreator.getTradeItemAdd((byte) 0, tradeItem));
                if (trade.getPartner() != null) {
                    trade.getPartner().getChr().sendPacket(PacketCreator.getTradeItemAdd((byte) 1, tradeItem));
                }
            }
            return;
        }

        // ── WAITING FOR OWNER TO CLICK OK ─────────────────────────────────
        if (!entry.pendingTradeBotDone) {
            entry.pendingTradeTimerMs += BotMovementManager.cfg.TICK_MS;
            if (trade.isPartnerConfirmed()) {
                completeTradeAndThank(bot, trade);
                entry.pendingTradeBotDone = true;
                entry.pendingTradeTimerMs = 0;
            } else if (entry.pendingTradeTimerMs > 60_000) { // 60 s timeout
                BotManager.getInstance().botSay(bot, "trade timed out, cancelling");
                Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
                resetTradeState(entry);
            }
        }
        // pendingTradeBotDone=true: wait for bot.getTrade() to become null (handled above)
    }

    private static void cancelTradeSequence(BotEntry entry, Character bot, String msg) {
        BotManager.getInstance().botSay(bot, msg);
        if (bot.getTrade() != null) Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE);
        resetTradeState(entry);
    }

    private static void resetTradeState(BotEntry entry) {
        entry.pendingTradeCategory = null;
        entry.pendingTradeItems    = null;
        entry.pendingTradeIdx      = 0;
        entry.pendingTradeTimerMs  = 0;
        entry.pendingTradeAllAdded = false;
        entry.pendingTradeBotDone  = false;
    }

    private static void completeTradeAndThank(Character bot, Trade trade) {
        boolean receivedSomething = trade.getPartner() != null && trade.getPartner().hasAnyOffer();
        Trade.completeTrade(bot);
        if (receivedSomething) {
            BotManager.getInstance().botSay(bot, BotManager.randomReply(TRADE_THANKS));
        }
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static List<Item> collectItems(String category, Character bot) {
        List<Item> result = new ArrayList<>();
        switch (category) {
            case "scrolls" -> collectFromBag(bot, result, InventoryType.USE,
                    item -> item.getItemId() >= 2040000 && item.getItemId() < 2050000);
            case "pots"    -> collectFromBag(bot, result, InventoryType.USE,
                    item -> item.getItemId() >= 2000000 && item.getItemId() < 2023000);
            case "use"     -> collectFromBag(bot, result, InventoryType.USE, item -> true);
            case "equips"  -> collectFromBag(bot, result, InventoryType.EQUIP, item -> true);
            case "etc"     -> collectFromBag(bot, result, InventoryType.ETC,   item -> true);
            default -> {
                if (category.startsWith("name:")) {
                    String lower = category.substring(5).toLowerCase();
                    ItemInformationProvider ii = ItemInformationProvider.getInstance();
                    for (InventoryType t : List.of(
                            InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
                        collectFromBag(bot, result, t, item -> {
                            String name = ii.getName(item.getItemId());
                            return name != null && name.toLowerCase().contains(lower);
                        });
                    }
                }
            }
        }
        return result;
    }

    private static void collectFromBag(Character bot, List<Item> result,
                                       InventoryType type, Predicate<Item> filter) {
        Inventory inv = bot.getInventory(type);
        for (short slot = 1; slot <= inv.getSlotLimit(); slot++) {
            Item item = inv.getItem(slot);
            if (item != null && isSafeToDrop(item) && filter.test(item)) result.add(item);
        }
    }

    // ─── Drop actions (floor) ─────────────────────────────────────────────────

    static void dropScrolls(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.USE,
                item -> item.getItemId() >= 2040000 && item.getItemId() < 2050000);
        reply(bot, count, "scroll");
    }

    static void dropPotions(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.USE,
                item -> item.getItemId() >= 2000000 && item.getItemId() < 2023000);
        reply(bot, count, "potion");
    }

    static void dropEquips(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.EQUIP, item -> true);
        BotManager.getInstance().botSay(bot,
                count > 0 ? "dropped " + count + " equip" + (count != 1 ? "s" : "") + "!"
                          : "equip bag is already empty");
    }

    static void dropEtc(BotEntry entry, Character bot) {
        int count = dropFromBag(bot, InventoryType.ETC, item -> true);
        reply(bot, count, "etc item");
    }

    static void dropByName(BotEntry entry, Character bot, String nameFragment) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        String lower = nameFragment.toLowerCase().trim();
        int total = 0;
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            total += dropFromBag(bot, type, item -> {
                String name = ii.getName(item.getItemId());
                return name != null && name.toLowerCase().contains(lower);
            });
        }
        BotManager.getInstance().botSay(bot,
                total > 0 ? "dropped " + total + "x '" + nameFragment + "'"
                          : "couldn't find '" + nameFragment + "' in my bags");
    }

    // ─── Inventory info ───────────────────────────────────────────────────────

    /** occupied/total for each bag: "equip: 10/24, use: 8/24, etc: 3/24, setup: 0/24" */
    static String slotsReport(Character bot) {
        StringBuilder sb = new StringBuilder();
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            Inventory inv = bot.getInventory(type);
            int used  = inv.getSlotLimit() - inv.getNumFreeSlot();
            int total = inv.getSlotLimit();
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(type.name().toLowerCase()).append(": ").append(used).append('/').append(total);
        }
        return sb.toString();
    }

    /** Full bag summary: "equip 10/24 | use 8/24 (3 scrolls, 5 pots) | etc 3/24" */
    static String inventorySummary(Character bot) {
        StringBuilder sb = new StringBuilder();
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            Inventory inv = bot.getInventory(type);
            int used  = inv.getSlotLimit() - inv.getNumFreeSlot();
            int total = inv.getSlotLimit();
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(type.name().toLowerCase()).append(' ').append(used).append('/').append(total);
            if (type == InventoryType.USE) {
                int scrolls = 0, pots = 0;
                for (Item item : inv.list()) {
                    int id = item.getItemId();
                    if (id >= 2040000 && id < 2050000) scrolls += item.getQuantity();
                    else if (id >= 2000000 && id < 2023000) pots += item.getQuantity();
                }
                if (scrolls > 0 || pots > 0) {
                    sb.append(" (");
                    if (scrolls > 0) sb.append(scrolls).append(scrolls != 1 ? " scrolls" : " scroll");
                    if (scrolls > 0 && pots > 0) sb.append(", ");
                    if (pots > 0) sb.append(pots).append(pots != 1 ? " pots" : " pot");
                    sb.append(')');
                }
            }
        }
        return sb.toString();
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private static int dropFromBag(Character bot, InventoryType type, Predicate<Item> filter) {
        Inventory inv = bot.getInventory(type);
        List<Short> slots = new ArrayList<>();
        for (short slot = 1; slot <= inv.getSlotLimit(); slot++) {
            Item item = inv.getItem(slot);
            if (item != null && isSafeToDrop(item) && filter.test(item)) slots.add(slot);
        }
        int count = 0;
        for (short slot : slots) {
            Item item = inv.getItem(slot);
            if (item == null) continue;
            InventoryManipulator.drop(bot.getClient(), type, slot, item.getQuantity());
            count++;
        }
        return count;
    }

    private static boolean isSafeToDrop(Item item) {
        if (item.isUntradeable()) return false;
        if (ItemInformationProvider.getInstance().isQuestItem(item.getItemId())) return false;
        return true;
    }

    private static void reply(Character bot, int count, String noun) {
        BotManager.getInstance().botSay(bot,
                count > 0 ? "dropped " + count + " " + noun + (count != 1 ? "s" : "") + "!"
                          : "no " + noun + "s to drop");
    }
}
