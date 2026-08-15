package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import server.Trade;
import server.agents.economy.social.TradeExecutionGateway;
import server.agents.economy.social.TradeOffer;
import server.economy.EconomyOperationContext;
import server.economy.EconomyItemEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Executes an agreed negotiation through Cosmic's real two-party Trade lifecycle. */
public final class CosmicNegotiatedTradeExecutor implements TradeExecutionGateway {
    private final Function<String, Character> characters;
    private final int interactionRangePixels;

    public CosmicNegotiatedTradeExecutor(Function<String, Character> characters, int interactionRangePixels) {
        this.characters = Objects.requireNonNull(characters);
        if (interactionRangePixels <= 0) throw new IllegalArgumentException();
        this.interactionRangePixels = interactionRangePixels;
    }

    @Override
    public Result execute(String idempotencyKey, String firstAgentId, TradeOffer firstOffer,
                          String secondAgentId, TradeOffer secondOffer) {
        return EconomyOperationContext.withParticipantFlags(true, true,
                () -> executeAttributed(idempotencyKey, firstAgentId, firstOffer, secondAgentId, secondOffer));
    }

    /** Executes an arrangement against the exact fingerprint that was accepted from the stall. */
    public Result executeExactItem(String idempotencyKey, String buyerAgentId, long buyerMesos,
                                   String sellerAgentId, int itemId, String fingerprint,
                                   int quantity) {
        if (buyerMesos <= 0 || itemId <= 0 || fingerprint == null || fingerprint.isBlank()
                || quantity <= 0) throw new IllegalArgumentException("invalid exact trade agreement");
        return EconomyOperationContext.withParticipantFlags(true, true, () -> {
            Character buyer = characters.apply(buyerAgentId);
            Character seller = characters.apply(sellerAgentId);
            if (!canInteract(buyer, seller))
                return new Result(false, "", "counterparties are not physically nearby");
            if (buyer.getTrade() != null || seller.getTrade() != null)
                return new Result(false, "", "counterparty is already trading");
            try {
                Trade.startTrade(buyer, idempotencyKey);
                Trade.inviteTrade(buyer, seller);
                Trade.visitTrade(seller, buyer);
                if (buyer.getTrade() == null || seller.getTrade() == null
                        || !buyer.getTrade().isFullTrade() || !seller.getTrade().isFullTrade()) {
                    cancel(buyer);
                    return new Result(false, "", "Cosmic trade invitation was rejected");
                }
                if (!placeOffer(buyer, new TradeOffer(buyerMesos, Map.of()))
                        || !placeExactItem(seller, itemId, fingerprint, quantity)) {
                    cancel(buyer);
                    return new Result(false, "", "exact offered holding changed before settlement");
                }
                buyer.getTrade().chat("Exact offer placed for agreement " + idempotencyKey);
                seller.getTrade().chat("Exact offer accepted for agreement " + idempotencyKey);
                Trade.completeTrade(buyer); Trade.completeTrade(seller);
                boolean success = buyer.getTrade() == null && seller.getTrade() == null;
                return new Result(success, idempotencyKey,
                        success ? "Cosmic exact-item PLAYER_TRADE committed"
                                : "Cosmic exact-item trade did not settle");
            } catch (RuntimeException failure) {
                cancel(buyer);
                return new Result(false, idempotencyKey, failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage());
            }
        });
    }

    private Result executeAttributed(String idempotencyKey, String firstAgentId, TradeOffer firstOffer,
                                     String secondAgentId, TradeOffer secondOffer) {
        Character first = characters.apply(firstAgentId);
        Character second = characters.apply(secondAgentId);
        if (!canInteract(first, second)) return new Result(false, "", "counterparties are not physically nearby");
        if (first.getTrade() != null || second.getTrade() != null)
            return new Result(false, "", "counterparty is already trading");
        try {
            Trade.startTrade(first, idempotencyKey);
            Trade.inviteTrade(first, second);
            Trade.visitTrade(second, first);
            if (first.getTrade() == null || second.getTrade() == null
                    || !first.getTrade().isFullTrade() || !second.getTrade().isFullTrade()) {
                cancel(first);
                return new Result(false, "", "Cosmic trade invitation was rejected");
            }
            if (!placeOffer(first, firstOffer) || !placeOffer(second, secondOffer)) {
                cancel(first);
                return new Result(false, "", "offered holdings changed before settlement");
            }
            first.getTrade().chat("Offer placed for agreement " + idempotencyKey);
            second.getTrade().chat("Offer accepted for agreement " + idempotencyKey);
            Trade.completeTrade(first);
            Trade.completeTrade(second);
            boolean success = first.getTrade() == null && second.getTrade() == null;
            return new Result(success, idempotencyKey,
                    success ? "Cosmic PLAYER_TRADE committed" : "Cosmic trade did not settle");
        } catch (RuntimeException failure) {
            cancel(first);
            return new Result(false, idempotencyKey, failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    private boolean canInteract(Character first, Character second) {
        if (first == null || second == null || first == second || first.getClient() == null
                || second.getClient() == null || first.getMap() == null || first.getMap() != second.getMap()
                || first.getMapId() < 910000000 || first.getMapId() > 910000022) return false;
        return first.getPosition().distanceSq(second.getPosition())
                <= (long) interactionRangePixels * interactionRangePixels;
    }

    private static boolean placeOffer(Character character, TradeOffer offer) {
        Trade trade = character.getTrade();
        trade.setMeso(Math.toIntExact(offer.mesos()));
        if (trade.getOfferedMesos() != offer.mesos()) return false;
        List<Selection> selections = selectItems(character, offer.items());
        if (selections == null || selections.size() > 9) return false;
        byte targetSlot = 1;
        for (Selection selection : selections) {
            Item tradeItem = selection.item().copy();
            tradeItem.setQuantity(selection.quantity());
            tradeItem.setPosition(targetSlot++);
            if (!trade.addItem(tradeItem)) return false;
            InventoryManipulator.removeFromSlot(character.getClient(), selection.type(),
                    selection.item().getPosition(), selection.quantity(), true);
        }
        return true;
    }

    private static List<Selection> selectItems(Character character, Map<Integer, Integer> requested) {
        List<Selection> selections = new ArrayList<>();
        for (Map.Entry<Integer, Integer> request : requested.entrySet()) {
            InventoryType type = ItemConstants.getInventoryType(request.getKey());
            Inventory inventory = character.getInventory(type);
            int remaining = request.getValue();
            for (Item item : inventory.listById(request.getKey())) {
                if (remaining == 0) break;
                short quantity = (short) Math.min(remaining, item.getQuantity());
                selections.add(new Selection(type, item, quantity));
                remaining -= quantity;
            }
            if (remaining != 0) return null;
        }
        return selections;
    }

    private static boolean placeExactItem(Character seller, int itemId, String fingerprint, int quantity) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        Item exact = seller.getInventory(type).listById(itemId).stream()
                .filter(item -> EconomyItemEvidence.describe(item).fingerprint().equals(fingerprint))
                .filter(item -> item.getQuantity() >= quantity).findFirst().orElse(null);
        if (exact == null || quantity > Short.MAX_VALUE) return false;
        Item tradeItem = exact.copy(); tradeItem.setQuantity((short) quantity); tradeItem.setPosition((byte) 1);
        if (!seller.getTrade().addItem(tradeItem)) return false;
        InventoryManipulator.removeFromSlot(seller.getClient(), type, exact.getPosition(),
                (short) quantity, true);
        return true;
    }

    private static void cancel(Character character) {
        if (character != null && character.getTrade() != null)
            Trade.cancelTrade(character, Trade.TradeResult.NO_RESPONSE);
    }

    private record Selection(InventoryType type, Item item, short quantity) { }
}
