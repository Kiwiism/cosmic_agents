package server.agents.economy.market;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.integration.cosmic.CosmicMarketObservationService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StallAndKnowledgeTest {
    @Test
    void requiresPhysicalPresenceAndOneStallPerAgent() {
        StallRegistry registry = new StallRegistry(16);
        MarketListing listing = new MarketListing("listing", "lot", 4000000, 10, 100,
                Instant.EPOCH, EconomicReason.QUEST_REQUIREMENT);
        assertThrows(IllegalStateException.class, () -> registry.open(
                "stall", "agent", 910000001, 10, Instant.EPOCH, false, List.of(listing)));
        registry.open("stall", "agent", 910000001, 10, Instant.EPOCH, true, List.of(listing));
        assertThrows(IllegalStateException.class, () -> registry.open(
                "stall-2", "agent", 910000002, 20, Instant.EPOCH, true, List.of(listing)));
        assertEquals(1, registry.inRoom(910000001).size());
    }

    @Test
    void beliefsContainOnlyObservedListingsAndExpire() {
        PrivateMarketKnowledge knowledge = new PrivateMarketKnowledge();
        knowledge.observe(new MarketObservation("o", "buyer", Instant.EPOCH, 910000001,
                "seller", "listing", 4000000, 1, 200, MarketObservation.State.LISTED));
        assertEquals(200, knowledge.observedMedianAsk(4000000, Instant.EPOCH.plusSeconds(10),
                Duration.ofMinutes(1)));
        assertEquals(0, knowledge.observedMedianAsk(4000000, Instant.EPOCH.plusSeconds(120),
                Duration.ofMinutes(1)));
    }

    @Test
    void privateBeliefsAndPhysicalTripProgressRoundTripWithoutAdministrativeKnowledge() {
        MarketObservation observation = new MarketObservation("o", "buyer", Instant.EPOCH,
                910000001, "seller", "listing", 4000000, 2, 200,
                MarketObservation.State.LISTED);
        PrivateMarketKnowledge original = new PrivateMarketKnowledge();
        original.observe(observation);
        PrivateMarketKnowledge restored = PrivateMarketKnowledge.restore(original.snapshot());
        assertEquals(List.of(observation), restored.snapshot());

        PhysicalMarketTrip trip = new PhysicalMarketTrip(List.of(910000001, 910000002));
        PhysicalMarketTrip roundTrip = PhysicalMarketTrip.restore(trip.snapshot());
        assertEquals(trip.snapshot(), roundTrip.snapshot());
    }

    @Test
    void physicallyEntersAndDwellsHalfASecondForEveryListing() {
        Character agent = mock(Character.class);
        var stall = new FreeMarketPhysicalGateway.StallTarget(
                7, 22, 910000001, 100, 0);
        FreeMarketPhysicalGateway gateway = new FreeMarketPhysicalGateway() {
            @Override public ActionStatus requestEntrance(Character ignored) { return ActionStatus.ARRIVED; }
            @Override public ActionStatus requestRoom(Character ignored, int room) { return ActionStatus.ARRIVED; }
            @Override public ActionStatus requestApproach(Character ignored, StallTarget target) {
                return ActionStatus.ARRIVED;
            }
            @Override public List<StallTarget> visibleStalls(Character ignored) { return List.of(stall); }
            @Override public InspectionStatus enterStall(Character ignored, StallTarget target) {
                return new InspectionStatus(ActionStatus.ARRIVED, 3);
            }
            @Override public List<CosmicMarketObservationService.ObservedOffer> inspectAndExit(
                    Character ignored, String logicalAgentId, StallTarget target, Instant logicalAt,
                    PrivateMarketKnowledge knowledge) { return List.of(); }
            @Override public void cancelStallVisit(Character ignored, StallTarget target) { }
            @Override public PurchaseStatus buyObserved(Character ignored, String logicalAgentId,
                    CosmicMarketObservationService.ObservedOffer offer, short bundles, Instant logicalAt,
                    PrivateMarketKnowledge knowledge) {
                throw new AssertionError("purchase was not expected");
            }
        };
        PhysicalMarketTrip trip = new PhysicalMarketTrip(
                List.of(910000001), Duration.ofMillis(500));

        PhysicalMarketTrip.Step entered = trip.tick(
                agent, "agent-1", Instant.EPOCH, new PrivateMarketKnowledge(), gateway);
        PhysicalMarketTrip.Step waiting = trip.tick(
                agent, "agent-1", Instant.EPOCH.plusMillis(1_499), new PrivateMarketKnowledge(), gateway);
        PhysicalMarketTrip.Step observed = trip.tick(
                agent, "agent-1", Instant.EPOCH.plusMillis(1_500), new PrivateMarketKnowledge(), gateway);

        assertEquals(PhysicalMarketTrip.Status.INSPECTING, entered.status());
        assertTrue(entered.inspectionStarted());
        assertEquals(Instant.EPOCH.plusMillis(1_500), entered.revisitAt().orElseThrow());
        assertEquals(PhysicalMarketTrip.Status.INSPECTING, waiting.status());
        assertFalse(waiting.inspectionStarted());
        assertEquals(PhysicalMarketTrip.Status.OBSERVED, observed.status());
    }
}
