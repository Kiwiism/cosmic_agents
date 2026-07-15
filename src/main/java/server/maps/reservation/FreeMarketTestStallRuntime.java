package server.maps.reservation;

import client.Character;
import client.Client;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.maps.MapleMap;
import server.maps.PlayerShopItem;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class FreeMarketTestStallRuntime {
    private record TestStall(CharacterSpaceOwner reservationOwner, FreeMarketTestMerchant merchant) {
    }

    private static final int STORE_CAPACITY = 16;
    private static final AtomicInteger NEXT_RUNTIME_ID = new AtomicInteger(1_800_000_000);
    private static final int[] PERMIT_ITEM_IDS = {5030000, 5030001, 5030002, 5030004, 5030008, 5030010};
    private static final int[] TEST_ITEM_IDS = {
            2000000, 2000001, 2000002, 2000003, 2000006, 2001000, 2001001,
            2010000, 2010001, 2020000, 2020001, 2020002,
            2040000, 2040001, 2040300, 2040400, 2040500, 2040600,
            4000000, 4000001, 4000002, 4000003, 4000004, 4000005,
            4000006, 4000007, 4000008, 4000009, 4000010, 4000011,
            4010000, 4010001, 4010002, 4020000, 4020001, 4020002
    };

    private static final Map<CharacterSpaceScope, List<TestStall>> STALLS_BY_SCOPE = new HashMap<>();

    private FreeMarketTestStallRuntime() {
    }

    public static synchronized int fill(Character requester, int percentage) {
        if (requester == null || requester.getClient() == null
                || !FreeMarketCharacterSpaceCatalog.isRoom(requester.getMapId())
                || percentage < 0 || percentage > 100) {
            return -1;
        }
        CharacterSpaceScope scope = FreeMarketStorePlacementService.scope(requester);
        clear(requester);
        if (percentage == 0) {
            return 0;
        }

        List<CharacterSpace> candidates = new ArrayList<>(
                FreeMarketCharacterSpaceCatalog.spaces(requester.getMapId()));
        Collections.shuffle(candidates);
        int target = Math.max(1, (int) Math.round(candidates.size() * percentage / 100.0));
        List<TestStall> created = new ArrayList<>(target);
        MapleMap map = requester.getMap();

        for (CharacterSpace candidate : candidates) {
            if (created.size() >= target) {
                break;
            }
            int runtimeId = NEXT_RUNTIME_ID.getAndIncrement();
            CharacterSpaceOwner reservationOwner = CharacterSpaceOwner.testStall(runtimeId);
            var reservation = CharacterSpaceReservationRuntime.reserveExact(
                    scope, reservationOwner, candidates, candidate, 1);
            if (reservation.isEmpty()) {
                continue;
            }
            try {
                String label = "CH" + scope.channelId()
                        + " FM" + FreeMarketCharacterSpaceCatalog.roomNumber(scope.mapId())
                        + " S" + candidate.spotNumber();
                FreeMarketTestMerchant merchant = createMerchant(
                        map, scope, reservation.get(), runtimeId, label);
                map.addMapObject(merchant);
                merchant.setOpen(true);
                map.broadcastMessage(PacketCreator.spawnHiredMerchantBox(merchant));
                created.add(new TestStall(reservationOwner, merchant));
            } catch (RuntimeException failure) {
                CharacterSpaceReservationRuntime.release(reservationOwner);
                monitoring.RuntimeFailureLogger.log(failure);
            }
        }
        STALLS_BY_SCOPE.put(scope, List.copyOf(created));
        return created.size();
    }

    public static synchronized int clear(Character requester) {
        if (requester == null || requester.getClient() == null) {
            return 0;
        }
        CharacterSpaceScope scope = FreeMarketStorePlacementService.scope(requester);
        List<TestStall> stalls = STALLS_BY_SCOPE.remove(scope);
        if (stalls == null) {
            return 0;
        }
        MapleMap map = requester.getMap();
        for (TestStall stall : stalls) {
            stall.merchant().setOpen(false);
            map.broadcastMessage(PacketCreator.removeHiredMerchantBox(stall.merchant().getOwnerId()));
            map.removeMapObject(stall.merchant());
            CharacterSpaceReservationRuntime.release(stall.reservationOwner());
        }
        return stalls.size();
    }

    public static synchronized int count(Character requester) {
        if (requester == null || requester.getClient() == null) {
            return 0;
        }
        return STALLS_BY_SCOPE.getOrDefault(
                FreeMarketStorePlacementService.scope(requester), List.of()).size();
    }

    private static FreeMarketTestMerchant createMerchant(
            MapleMap map,
            CharacterSpaceScope scope,
            CharacterSpaceReservation reservation,
            int runtimeId,
            String label) {
        Client mockClient = Client.createMock();
        mockClient.setWorld(scope.worldId());
        mockClient.setChannel(scope.channelId());
        Character mockOwner = Character.getDefault(mockClient);
        mockClient.setPlayer(mockOwner);
        mockOwner.setName(label);
        mockOwner.setWorld(scope.worldId());
        mockOwner.setMap(scope.mapId());
        mockOwner.setMap(map);
        mockOwner.setPosition(reservation.position());

        int permitItemId = PERMIT_ITEM_IDS[
                ThreadLocalRandom.current().nextInt(PERMIT_ITEM_IDS.length)];
        FreeMarketTestMerchant merchant = new FreeMarketTestMerchant(
                mockOwner, runtimeId, label, label, permitItemId);
        merchant.setPosition(reservation.position());
        addRandomListings(merchant);
        return merchant;
    }

    private static void addRandomListings(FreeMarketTestMerchant merchant) {
        ItemInformationProvider itemInfo = ItemInformationProvider.getInstance();
        List<Integer> candidates = new ArrayList<>();
        for (int itemId : TEST_ITEM_IDS) {
            if (!ItemConstants.isRechargeable(itemId)
                    && !itemInfo.isUnmerchable(itemId)
                    && itemInfo.getPrice(itemId, 1) > 0) {
                candidates.add(itemId);
            }
        }
        Collections.shuffle(candidates);
        int listingCount = ThreadLocalRandom.current().nextInt(
                1, Math.min(STORE_CAPACITY, candidates.size()) + 1);
        for (int i = 0; i < listingCount; i++) {
            int itemId = candidates.get(i);
            short perBundle = (short) ThreadLocalRandom.current().nextInt(1, 11);
            short bundles = (short) ThreadLocalRandom.current().nextInt(1, 11);
            int price = itemInfo.getPrice(itemId, perBundle);
            merchant.addItem(new PlayerShopItem(
                    new Item(itemId, (short) 0, perBundle), bundles, price));
        }
    }
}
