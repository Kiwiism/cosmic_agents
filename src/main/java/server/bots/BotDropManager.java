package server.bots;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.game.GameConstants;
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
    private static final List<String> TRADE_INVITATION_MSGS = List.of(
            "k", "ok", "kk", "sure", "k, I inv", "k i inv",
            "omw", "inv u", "one sec", "coming");
    private static final List<String> TRADE_THANKS_MSGS = List.of(
            "ty!", "thanks!", "thank you!", "tyty", "appreciate it!", "tysm!",
            "nice ty", "ooh ty!", "thx!!", "much appreciated");
    private static final List<String> TRADE_FREEBIE_QUIPS = List.of(
            "i better get paid for that eventually lol",
            "you really should be paying me for that",
            "free delivery, where's my tip",
            "don't say i never gave you anything",
            "i'm basically your personal shopper at this point",
            "doing this for free smh");
    private static final List<String> NO_ITEMS_MSGS = List.of(
            "i don't have any %s",
            "no %s on me rn",
            "don't have any %s right now",
            "i'm out of %s",
            "none of that on me right now",
            "fresh out of %s",
            "wish i had %s but nope",
            "checked, no %s"
    );
    private static final List<String> ALL_DONE_MSGS = List.of(
            "that's all!", "done adding stuff!", "all set!", "everything's in!",
            "that's everything!", "done!", "added it all", "check it out"
    );

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

    /**
     * Kicks off a trade sequence for the given category.
     * Items are batched ≤9 per trade window; subsequent batches open new trades automatically.
     */
    static void startTradeTransfer(String category, BotEntry entry, Character bot) {
        if (isMesoCategory(category)) {
            startTradeMesoTransfer(category, entry, bot);
            return;
        }

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
        List<Item> items = collectItems(category, entry, bot);
        if (items.isEmpty()) {
            BotManager.getInstance().botSay(bot, noItemsReply(category));
            return;
        }
        startTradeSequence(category, owner, items, 0, false, entry, bot);
    }

    static void startTradeTransfer(Item item, Character recipient, BotEntry entry, Character bot) {
        if (recipient == null) {
            BotManager.getInstance().botSay(bot, "can't find who to trade!");
            return;
        }
        if (!hasItem(bot, item)) {
            BotManager.getInstance().botSay(bot, "don't have it anymore");
            return;
        }
        if (bot.getTrade() != null || entry.pendingTradeCategory != null) {
            BotManager.getInstance().botSay(bot, "already in a trade!");
            return;
        }
        if (recipient.getTrade() != null) {
            BotManager.getInstance().botSay(bot, recipient.getName() + " is already in a trade!");
            return;
        }

        startTradeSequence("loot_offer", recipient, List.of(item), 0, true, entry, bot);
    }

    static boolean hasTransferableItems(String category, BotEntry entry, Character bot) {
        if (isMesoCategory(category)) {
            int currentMesos = bot.getMeso();
            if (currentMesos <= 0) {
                return false;
            }

            int requestedMesos = requestedTradeMesos(category);
            return requestedMesos <= 0 || currentMesos >= requestedMesos;
        }

        return !collectItems(category, entry, bot).isEmpty();
    }

    static String noItemsReply(String category) {
        String what = switch (category) {
            case "mesos" -> "mesos";
            case "recommended" -> "better gear for you";
            case "scrolls" -> "scrolls";
            case "pots" -> "pots";
            case "use" -> "use items";
            case "equips" -> "equips";
            case "etc" -> "etc items";
            default -> {
                if (category.startsWith("mesos:")) {
                    yield "mesos";
                }
                yield category.startsWith("name:") ? category.substring(5) : "those items";
            }
        };

        String fmt = BotManager.randomReply(NO_ITEMS_MSGS);
        return String.format(fmt, what);
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
            BotManager.getInstance().botSay(bot, "can't find who to trade!");
            return;
        }
        entry.pendingTradeCategory = category;
        entry.pendingTradeRecipientId = recipient.getId();
        entry.pendingTradeSingleBatch = singleBatch;
        openTradeBatch(entry, bot, items, mesos);
    }

    private static void openTradeBatch(BotEntry entry, Character bot, List<Item> items, int mesos) {
        Character recipient = resolveTradeRecipient(entry, bot);
        if (recipient == null || recipient.getTrade() != null) {
            cancelTradeSequence(entry, bot, "can't trade right now, stopping");
            return;
        }
        entry.pendingTradeItems    = items.size() > 9 ? new ArrayList<>(items.subList(0, 9)) : new ArrayList<>(items);
        entry.pendingTradeMeso     = mesos;
        entry.pendingTradeIdx      = 0;
        entry.pendingTradeTimerMs  = 0;
        entry.pendingTradeMesoAdded = false;
        entry.pendingTradeAllAdded = false;
        entry.pendingTradeBotDone  = false;
        Trade.startTrade(bot);
        Trade.inviteTrade(bot, recipient);
        BotManager.getInstance().botSay(bot, BotManager.randomReply(TRADE_INVITATION_MSGS));
    }

    /** Called every bot simulation tick while a trade sequence is in progress. */
    static void tickTrade(BotEntry entry, Character bot) {
        if (entry.pendingTradeCategory == null) return;

        Trade trade = bot.getTrade();

        // ── PAUSE between batches (items == null) ──────────────────────────
        if (entry.pendingTradeItems == null) {
            if (entry.pendingTradeSingleBatch) {
                resetTradeState(entry);
                return;
            }
            if (entry.pendingTradeTimerMs > 0) {
                entry.pendingTradeTimerMs = BotMovementManager.tickDown(entry.pendingTradeTimerMs);
                return;
            }
            List<Item> next = collectItems(entry.pendingTradeCategory, entry, bot);
            if (next.isEmpty()) {
                resetTradeState(entry);
            } else {
                openTradeBatch(entry, bot, next, 0);
            }
            return;
        }

        // ── Trade was closed externally ────────────────────────────────────
        if (trade == null) {
            if (entry.pendingTradeBotDone) {
                // Both sides confirmed — sequence complete or cancelled after bot OK
                if (entry.pendingTradeSingleBatch) {
                    resetTradeState(entry);
                    return;
                }
                entry.pendingTradeItems    = null;
                entry.pendingTradeAllAdded = false;
                entry.pendingTradeBotDone  = false;
                entry.pendingTradeTimerMs  = BotMovementManager.delayAfterCurrentTick(1_000);
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

            if (!entry.pendingTradeMesoAdded && entry.pendingTradeMeso > 0) {
                if (bot.getMeso() < entry.pendingTradeMeso) {
                    cancelTradeSequence(entry, bot, "don't have that many mesos anymore");
                    return;
                }

                trade.setMeso(entry.pendingTradeMeso);
                entry.pendingTradeMesoAdded = true;
                entry.pendingTradeTimerMs = BotMovementManager.delayAfterCurrentTick(500);
                return;
            }

            List<Item> items = entry.pendingTradeItems;
            int idx = entry.pendingTradeIdx;

            if (idx >= items.size()) {
                // All items added — say so in trade chat and wait for owner OK
                entry.pendingTradeAllAdded = true;
                entry.pendingTradeTimerMs  = 0;
                String msg = BotManager.randomReply(ALL_DONE_MSGS);
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
        entry.pendingTradeRecipientId = 0;
        entry.pendingTradeMeso     = 0;
        entry.pendingTradeIdx      = 0;
        entry.pendingTradeTimerMs  = 0;
        entry.pendingTradeMesoAdded = false;
        entry.pendingTradeAllAdded = false;
        entry.pendingTradeBotDone  = false;
        entry.pendingTradeSingleBatch = false;
    }

    private static void completeTradeAndThank(Character bot, Trade trade) {
        boolean receivedSomething = trade.getPartner() != null && trade.getPartner().hasAnyOffer();
        Trade.completeTrade(bot);
        if (receivedSomething) {
            BotManager.getInstance().botSay(bot, BotManager.randomReply(TRADE_THANKS_MSGS));
        } else if (ThreadLocalRandom.current().nextInt(100) < 20) {
            BotManager.getInstance().botSay(bot, BotManager.randomReply(TRADE_FREEBIE_QUIPS));
        }
    }

    private static void startTradeMesoTransfer(String category, BotEntry entry, Character bot) {
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

        int currentMesos = bot.getMeso();
        if (currentMesos <= 0) {
            BotManager.getInstance().botSay(bot, noItemsReply(category));
            return;
        }

        int requestedMesos = requestedTradeMesos(category);
        if (requestedMesos == 0) {
            BotManager.getInstance().botSay(bot, "ask for more than 0 mesos, or just say 'trade mesos'");
            return;
        }
        if (requestedMesos > 0 && currentMesos < requestedMesos) {
            BotManager.getInstance().botSay(bot, notEnoughMesosReply(requestedMesos, currentMesos));
            return;
        }

        startTradeSequence(category, owner, List.of(), requestedMesos > 0 ? requestedMesos : currentMesos, true, entry, bot);
    }

    // ─── Item collection helpers ──────────────────────────────────────────────

    private static List<Item> collectItems(String category, BotEntry entry, Character bot) {
        List<Item> result = new ArrayList<>();
        switch (category) {
            case "recommended" -> {
                Character owner = entry.owner;
                if (owner != null) {
                    result.addAll(BotEquipManager.collectRecommendedItems(owner, bot));
                }
            }
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

    static boolean hasItem(Character bot, Item item) {
        if (bot == null || item == null) {
            return false;
        }

        Inventory inv = bot.getInventory(item.getInventoryType());
        if (inv == null) {
            return false;
        }

        Item current = inv.getItem(item.getPosition());
        return current == item;
    }

    private static Character resolveTradeRecipient(BotEntry entry, Character bot) {
        int recipientId = entry.pendingTradeRecipientId;
        if (recipientId <= 0) {
            return entry.owner;
        }

        Character owner = entry.owner;
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

    static boolean isMesoCategory(String category) {
        return category != null && (category.equals("mesos") || category.startsWith("mesos:"));
    }

    private static int requestedTradeMesos(String category) {
        if (!isMesoCategory(category)) {
            return 0;
        }
        if ("mesos".equals(category)) {
            return -1;
        }

        try {
            return Integer.parseInt(category.substring("mesos:".length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String notEnoughMesosReply(int requestedMesos, int currentMesos) {
        return "i only have " + GameConstants.numberWithCommas(currentMesos)
                + " mesos rn, not " + GameConstants.numberWithCommas(requestedMesos);
    }
}
