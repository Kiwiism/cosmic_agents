package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.maps.MapleMap;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.PlayerShop;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmicFreeMarketPhysicalGatewayTest {
    private final CosmicMarketObservationService observations = mock(CosmicMarketObservationService.class);
    private final CosmicFreeMarketPhysicalGateway gateway = new CosmicFreeMarketPhysicalGateway(
            observations, 910000000, 910000001, 910000022, 30_000, 20_000, 120);

    @Test
    void validatesRoomBoundsAndRecognizesArrivalWithoutTeleporting() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(910000007);

        assertEquals(FreeMarketPhysicalGateway.ActionStatus.ARRIVED, gateway.requestRoom(agent, 910000007));
        assertThrows(IllegalArgumentException.class, () -> gateway.requestRoom(agent, 910000023));
    }

    @Test
    void refusesRoomTravelWhenCharacterIsOutsideFreeMarket() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(100000000);

        assertEquals(FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE,
                gateway.requestRoom(agent, 910000001));
    }

    @Test
    void returnsOnlyOpenNonOwnedStallsInStableObjectOrder() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        PlayerShop later = shop(12, 102, false, true, new Point(40, 5));
        PlayerShop earlier = shop(4, 101, false, true, new Point(20, 5));
        PlayerShop owned = shop(2, 100, true, true, new Point(10, 5));
        PlayerShop closed = shop(3, 103, false, false, new Point(15, 5));
        when(agent.getMapId()).thenReturn(910000003);
        when(agent.getMap()).thenReturn(map);
        when(map.getMapObjects()).thenReturn(List.<MapObject>of(later, owned, closed, earlier));

        List<FreeMarketPhysicalGateway.StallTarget> result = gateway.visibleStalls(agent);

        assertEquals(List.of(4, 12), result.stream().map(FreeMarketPhysicalGateway.StallTarget::objectId).toList());
        assertEquals(101, result.getFirst().ownerCharacterId());
        assertEquals(910000003, result.getFirst().roomMapId());
    }

    @Test
    void optionalMotionLayerCannotReorderCommerceItinerary() {
        CosmicFreeMarketPhysicalGateway styled = new CosmicFreeMarketPhysicalGateway(
                observations, 910000000, 910000001, 910000022, 30_000, 20_000, 120,
                new SoloMaplingInspiredMarketInteractionBehavior(24));
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        PlayerShop far = shop(4, 101, false, true, new Point(500, 0));
        PlayerShop near = shop(12, 102, false, true, new Point(50, 0));
        when(agent.getId()).thenReturn(77);
        when(agent.getMapId()).thenReturn(910000003);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(map.getMapObjects()).thenReturn(List.<MapObject>of(far, near));

        assertEquals(List.of(4, 12), styled.visibleStalls(agent).stream()
                .map(FreeMarketPhysicalGateway.StallTarget::objectId).toList());
    }

    @Test
    void approachRejectsStaleShopAndAcceptsNearbyOpenShop() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        PlayerShop shop = shop(9, 101, false, true, new Point(100, 100));
        when(agent.getMapId()).thenReturn(910000002);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(110, 100));
        when(map.getMapObject(9)).thenReturn(shop);
        var target = new FreeMarketPhysicalGateway.StallTarget(9, 101, 910000002, 100, 100);

        assertEquals(FreeMarketPhysicalGateway.ActionStatus.ARRIVED, gateway.requestApproach(agent, target));
        when(shop.isOpen()).thenReturn(false);
        assertEquals(FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE, gateway.requestApproach(agent, target));
    }

    @Test
    void entersActualPlayerShopVisitorStateAndExitsAfterExactInspection() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        PlayerShop shop = shop(9, 101, false, true, new Point(100, 100));
        when(agent.getMapId()).thenReturn(910000002);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(110, 100));
        when(map.getMapObject(9)).thenReturn(shop);
        when(shop.visitShop(agent)).thenReturn(true);
        when(shop.isVisitor(agent)).thenReturn(false, true);
        when(shop.listingSnapshot()).thenReturn(List.of(mock(PlayerShop.ListingView.class),
                mock(PlayerShop.ListingView.class)));
        when(observations.inspectStall(eq(agent), eq("agent-1"), eq(9), any(), any()))
                .thenReturn(List.of());
        var target = new FreeMarketPhysicalGateway.StallTarget(9, 101, 910000002, 100, 100);

        assertEquals(2, gateway.enterStall(agent, target).listingCount());
        gateway.inspectAndExit(agent, "agent-1", target, java.time.Instant.EPOCH,
                new server.agents.economy.market.PrivateMarketKnowledge());

        verify(shop).visitShop(agent);
        verify(shop).removeVisitor(agent);
        verify(agent).setPlayerShop(null);
    }

    private static PlayerShop shop(int objectId, int ownerId, boolean owner, boolean open, Point position) {
        PlayerShop shop = mock(PlayerShop.class);
        when(shop.getObjectId()).thenReturn(objectId);
        when(shop.getOwnerId()).thenReturn(ownerId);
        when(shop.getType()).thenReturn(MapObjectType.SHOP);
        when(shop.isOwner(org.mockito.ArgumentMatchers.any())).thenReturn(owner);
        when(shop.isOpen()).thenReturn(open);
        when(shop.getPosition()).thenReturn(position);
        return shop;
    }
}
