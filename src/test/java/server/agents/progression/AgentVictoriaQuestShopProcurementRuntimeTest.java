package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.events.AgentEventBus;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentVictoriaQuestShopProcurementRuntimeTest {
    @Test
    void selectsTheNearestReachableVendorBeforeComparingPrice() {
        AgentVictoriaQuestRuntimeCatalog.ShopProcurementObjective objective =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                        .find(2165).orElseThrow().shopProcurementObjectives().getFirst();

        AgentVictoriaQuestRuntimeCatalog.ShopSource source =
                AgentVictoriaQuestShopProcurementRuntime.selectSource(120000300, objective);

        assertEquals(120000200, source.mapId());
        assertEquals(1091002, source.npcId());
        assertEquals(620, source.unitPrice());
    }

    @Test
    void startsAnItemOnlyVisitThroughTheExistingNpcShopCapability() {
        AgentVictoriaQuestRuntimeCatalog.ShopProcurementObjective objective =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                        .find(2165).orElseThrow().shopProcurementObjectives().getFirst();
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentEventBus eventBus = mock(AgentEventBus.class);
        when(agent.getMapId()).thenReturn(120000200);
        when(agent.getId()).thenReturn(17);
        when(gateway.itemCount(agent, 2000006)).thenReturn(0);

        try (MockedStatic<AgentVictoriaRouteRuntime> routes =
                     mockStatic(AgentVictoriaRouteRuntime.class);
             MockedStatic<AgentShopStateRuntime> shopState =
                     mockStatic(AgentShopStateRuntime.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class);
             MockedStatic<AgentSessionEventRuntime> events =
                    mockStatic(AgentSessionEventRuntime.class)) {
            routes.when(() -> AgentVictoriaRouteRuntime.travelStatus(
                            entry, agent, 120000200, gateway, 1_000L))
                    .thenReturn(new AgentVictoriaRouteRuntime.TravelOutcome(
                            AgentVictoriaRouteRuntime.Status.ARRIVED,
                            120000200, 120000200, 120000200, false));
            shopState.when(() -> AgentShopStateRuntime.shopVisitPending(entry))
                    .thenReturn(false);
            shops.when(() -> AgentShopService.requestVisitAtNpc(
                            entry, agent, 1091002, 0, 2000006, 1))
                    .thenReturn(true);
            events.when(() -> AgentSessionEventRuntime.bus(entry)).thenReturn(eventBus);

            AgentVictoriaQuestShopProcurementRuntime.Outcome outcome =
                    AgentVictoriaQuestShopProcurementRuntime.tick(
                            entry, agent, 2165, objective, false, gateway, 1_000L);

            assertEquals(AgentVictoriaQuestShopProcurementRuntime.Status.RUNNING,
                    outcome.status());
            assertTrue(outcome.purchaseStarted());
            shops.verify(() -> AgentShopService.requestVisitAtNpc(
                    entry, agent, 1091002, 0, 2000006, 1));
        }
    }
}
