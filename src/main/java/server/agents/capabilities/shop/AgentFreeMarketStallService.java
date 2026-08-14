package server.agents.capabilities.shop;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.ItemRestrictionPolicy;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;
import server.maps.PlayerShop;
import server.maps.PlayerShopEscrowSnapshot;
import server.maps.PlayerShopEscrowStore;
import server.maps.PlayerShopItem;
import server.maps.reservation.CharacterSpaceReservation;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AgentFreeMarketStallService {
    public static final int MAX_LISTINGS = 16;

    public record Listing(
            InventoryType inventoryType,
            short slot,
            short perBundle,
            short bundles,
            int price) {
        public Listing {
            if (inventoryType == null || slot <= 0 || perBundle <= 0 || bundles <= 0 || price < 0) {
                throw new IllegalArgumentException("agent stall listing is invalid");
            }
        }
    }

    public record Result(boolean success, String message, PlayerShop shop) {
        private static Result failed(String message) {
            return new Result(false, message, null);
        }
    }

    private record PreparedListing(
            InventoryType inventoryType,
            short slot,
            short removalQuantity,
            PlayerShopItem shopItem) {
    }

    public Result open(
            Character agent,
            String description,
            int permitItemId,
            List<Listing> listings,
            CharacterSpaceReservation reservation) {
        if (agent == null || agent.getClient() == null || reservation == null
                || reservation.scope().mapId() != agent.getMapId()) {
            return Result.failed("agent is not at the reserved Free Market map");
        }
        if (!ItemConstants.isPlayerShop(permitItemId)
                || agent.getInventory(InventoryType.CASH).countById(permitItemId) < 1) {
            return Result.failed("agent requires a valid player-shop permit");
        }
        if (agent.getPlayerShop() != null || agent.getHiredMerchant() != null) {
            return Result.failed("agent already has an active shop");
        }
        List<PreparedListing> prepared = prepare(agent, listings);
        if (prepared == null) {
            return Result.failed("one or more agent stall listings are no longer valid");
        }

        PlayerShop shop = new PlayerShop(agent, description == null ? "" : description, permitItemId);
        shop.enableDurableEscrow(UUID.randomUUID().toString());
        shop.setPosition(reservation.position());
        for (PreparedListing listing : prepared) {
            if (!shop.addItem(listing.shopItem())) {
                return Result.failed("agent stall capacity was exceeded");
            }
        }
        String summary = "map=" + agent.getMapId() + " listings=" + prepared.size()
                + " items=" + prepared.stream().map(listing ->
                listing.shopItem().getItem().getItemId() + "x" + listing.removalQuantity()).toList();
        EconomyTransactionCoordinator.execute("player-shop-open:" + shop.getEscrowId(), agent, null,
                EconomyOperationKind.PLAYER_SHOP_LIST, summary, context -> {
                    for (PreparedListing listing : prepared) {
                        InventoryManipulator.removeFromSlot(agent.getClient(), listing.inventoryType(),
                                listing.slot(), listing.removalQuantity(), true);
                    }
                    if (YamlConfig.config.server.USE_ERASE_PERMIT_ON_OPENSHOP) {
                        InventoryManipulator.removeById(
                                agent.getClient(), InventoryType.CASH, permitItemId, 1, true, false);
                    }
                    PlayerShopEscrowSnapshot escrow = PlayerShopEscrowSnapshot.capture(shop);
                    context.enlist(connection -> PlayerShopEscrowStore.persist(connection, escrow), () -> { });
                });

        agent.setPlayerShop(shop);
        agent.getMap().addMapObject(shop);
        agent.getWorldServer().registerPlayerShop(shop);
        shop.setOpen(true);
        agent.getMap().broadcastMessage(PacketCreator.updatePlayerShopBox(shop));
        return new Result(true, "agent player shop opened", shop);
    }

    /** Returns inventory from an interrupted prior stall before any new stall can be opened. */
    public boolean recoverInterruptedEscrow(Character agent) {
        var stored = PlayerShopEscrowStore.load(agent.getId());
        if (stored.isEmpty()) return false;
        PlayerShopEscrowSnapshot escrow = stored.orElseThrow();
        EconomyTransactionCoordinator.execute("player-shop-recover:" + escrow.escrowId(), agent, null,
                EconomyOperationKind.PLAYER_SHOP_LIST, "recover escrow=" + escrow.escrowId(), context -> {
                    for (PlayerShopEscrowSnapshot.Listing listing : escrow.listings()) {
                        Item item = listing.item().toItem();
                        int total = Math.multiplyExact(item.getQuantity(), listing.bundles());
                        if (total > Short.MAX_VALUE) throw new IllegalStateException("escrow stack exceeds limit");
                        item.setQuantity((short) total);
                        if (!InventoryManipulator.addFromDrop(agent.getClient(), item, false)) {
                            throw new IllegalStateException("inventory has no room for recovered stall escrow");
                        }
                    }
                    context.enlist(connection -> PlayerShopEscrowStore.delete(connection,
                            agent.getId(), escrow.escrowId()), () -> { });
                });
        return true;
    }

    private List<PreparedListing> prepare(Character agent, List<Listing> listings) {
        if (listings == null || listings.isEmpty() || listings.size() > MAX_LISTINGS) {
            return null;
        }
        ItemInformationProvider itemInfo = ItemInformationProvider.getInstance();
        Set<String> usedSlots = new HashSet<>();
        List<PreparedListing> prepared = new ArrayList<>(listings.size());
        for (Listing listing : listings) {
            String slotKey = listing.inventoryType().getType() + ":" + listing.slot();
            if (!usedSlots.add(slotKey)) {
                return null;
            }
            Item inventoryItem = agent.getInventory(listing.inventoryType()).getItem(listing.slot());
            int removalQuantity = listing.perBundle() * listing.bundles();
            if (inventoryItem == null || inventoryItem.getQuantity() < removalQuantity
                    || (inventoryItem.isUntradeable()
                    && !ItemRestrictionPolicy.allowsUntradeable(agent, inventoryItem.getItemId()))
                    || itemInfo.isUnmerchable(inventoryItem.getItemId())
                    || ItemConstants.isRechargeable(inventoryItem.getItemId())) {
                return null;
            }
            Item sellItem = inventoryItem.copy();
            sellItem.setQuantity(listing.perBundle());
            int price = listing.price() > 0
                    ? listing.price()
                    : itemInfo.getPrice(inventoryItem.getItemId(), listing.perBundle());
            if (price <= 0 || removalQuantity > Short.MAX_VALUE) {
                return null;
            }
            prepared.add(new PreparedListing(
                    listing.inventoryType(),
                    listing.slot(),
                    (short) removalQuantity,
                    new PlayerShopItem(sellItem, listing.bundles(), price)));
        }
        return prepared;
    }
}
