package server.partner;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.capabilities.build.AgentMakerService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.integration.AgentInventoryRuntimeAdapters;
import server.agents.plans.AgentScriptTaskQueueService;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.partner.PartnerAgentLifecycleBridge.SpawnedPartner;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CosmicPartnerAgentLifecycleBridgeTest {
    @BeforeEach
    void resetRuntimeState() {
        clearRuntimeState();
    }

    @AfterEach
    void clearRuntimeState() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        PartnerInteractionPolicy.clearPendingActivationsForTests();
    }

    @Test
    void spawnFailureAlwaysReleasesPendingActivation() {
        Character owner = character(10);
        RuntimeException failure = new RuntimeException("spawn failed");

        try (MockedStatic<AgentInteractionRuntime> interaction =
                     mockStatic(AgentInteractionRuntime.class)) {
            interaction.when(() -> AgentInteractionRuntime.spawnPartnerAgentForLeader(
                    owner, "Partner")).thenThrow(failure);

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> CosmicPartnerAgentLifecycleBridge.INSTANCE.spawnFollowing(
                            owner, 20, "Partner"));

            assertSame(failure, thrown);
        }

        assertTrue(PartnerInteractionPolicy.reservePendingActivation(20, 30));
    }

    @Test
    void successfulSpawnReleasesReservationAfterPublishingPartnerMarker() {
        Character owner = character(40);
        Character partner = character(50);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(partner, owner, null);
        AgentRuntimeRegistry.mutableEntriesForLeader(owner.getId()).add(entry);
        AgentLifecycleService.AgentSpawnResult spawn = new AgentLifecycleService.AgentSpawnResult(
                true, partner, false, null);
        AgentTradeLifecycleRuntimeService.RuntimeCallbacks callbacks =
                mock(AgentTradeLifecycleRuntimeService.RuntimeCallbacks.class);

        try (MockedStatic<AgentInteractionRuntime> interaction =
                     mockStatic(AgentInteractionRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class);
             MockedStatic<AgentInventoryRuntimeAdapters> inventoryAdapters =
                     mockStatic(AgentInventoryRuntimeAdapters.class);
             MockedStatic<AgentTradeLifecycleRuntimeService> tradeLifecycle =
                     mockStatic(AgentTradeLifecycleRuntimeService.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class);
             MockedStatic<AgentMakerService> maker = mockStatic(AgentMakerService.class);
             MockedStatic<AgentScriptTaskQueueService> tasks =
                     mockStatic(AgentScriptTaskQueueService.class);
             MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            interaction.when(() -> AgentInteractionRuntime.spawnPartnerAgentForLeader(
                    owner, "Partner")).thenReturn(spawn);
            inventoryAdapters.when(AgentInventoryRuntimeAdapters::tradeLifecycleRuntimeCallbacks)
                    .thenReturn(callbacks);

            SpawnedPartner result = CosmicPartnerAgentLifecycleBridge.INSTANCE.spawnFollowing(
                    owner, partner.getId(), "Partner");

            assertSame(partner, result.character());
            assertSame(entry, result.runtimeEntry());
            assertTrue(entry.isPartnerManaged());
        }

        assertTrue(PartnerInteractionPolicy.reservePendingActivation(partner.getId(), 60));
    }

    @Test
    void failedReuseRestoresGenericAgentMarker() {
        Character owner = character(61);
        Character partner = character(62);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(partner, owner, null);
        AgentRuntimeRegistry.mutableEntriesForLeader(owner.getId()).add(entry);
        RuntimeException failure = new RuntimeException("reuse failed");
        AgentTradeLifecycleRuntimeService.RuntimeCallbacks callbacks =
                mock(AgentTradeLifecycleRuntimeService.RuntimeCallbacks.class);

        try (MockedStatic<AgentInteractionRuntime> interaction =
                     mockStatic(AgentInteractionRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class);
             MockedStatic<AgentInventoryRuntimeAdapters> inventoryAdapters =
                     mockStatic(AgentInventoryRuntimeAdapters.class);
             MockedStatic<AgentTradeLifecycleRuntimeService> tradeLifecycle =
                     mockStatic(AgentTradeLifecycleRuntimeService.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class);
             MockedStatic<AgentMakerService> maker = mockStatic(AgentMakerService.class);
             MockedStatic<AgentScriptTaskQueueService> tasks =
                     mockStatic(AgentScriptTaskQueueService.class);
             MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            interaction.when(() -> AgentInteractionRuntime.spawnPartnerAgentForLeader(
                    owner, "Partner")).thenThrow(failure);
            inventoryAdapters.when(AgentInventoryRuntimeAdapters::tradeLifecycleRuntimeCallbacks)
                    .thenReturn(callbacks);

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> CosmicPartnerAgentLifecycleBridge.INSTANCE.spawnFollowing(
                            owner, partner.getId(), "Partner"));

            assertSame(failure, thrown);
            assertFalse(entry.isPartnerManaged());
        }
    }

    @Test
    void competingActivationIsRejectedWithoutClearingOriginalReservation() {
        Character competingOwner = character(70);
        int partnerCharacterId = 80;
        assertTrue(PartnerInteractionPolicy.reservePendingActivation(
                partnerCharacterId, 90));

        try (MockedStatic<AgentInteractionRuntime> interaction =
                     mockStatic(AgentInteractionRuntime.class)) {
            assertThrows(IllegalStateException.class,
                    () -> CosmicPartnerAgentLifecycleBridge.INSTANCE.spawnFollowing(
                            competingOwner, partnerCharacterId, "Partner"));
            interaction.verifyNoInteractions();
        }

        PartnerInteractionPolicy.releasePendingActivation(partnerCharacterId, 90);
        assertTrue(PartnerInteractionPolicy.reservePendingActivation(
                partnerCharacterId, competingOwner.getId()));
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
