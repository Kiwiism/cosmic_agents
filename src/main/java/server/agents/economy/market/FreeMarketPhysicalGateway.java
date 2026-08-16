package server.agents.economy.market;

import client.Character;
import server.agents.economy.integration.cosmic.CosmicMarketObservationService;

import java.time.Instant;
import java.util.List;

/** Replaceable boundary for real FM portal travel, walking, observation, and purchase. */
public interface FreeMarketPhysicalGateway {
    ActionStatus requestRoom(Character agent, int roomMapId);
    ActionStatus requestApproach(Character agent, StallTarget stall);
    List<StallTarget> visibleStalls(Character agent);
    List<CosmicMarketObservationService.ObservedOffer> inspectNearby(
            Character agent, String logicalAgentId, Instant logicalAt, PrivateMarketKnowledge knowledge);
    PurchaseStatus buyObserved(Character agent, String logicalAgentId,
                               CosmicMarketObservationService.ObservedOffer offer, short bundles,
                               Instant logicalAt, PrivateMarketKnowledge knowledge);

    enum ActionStatus { ARRIVED, ASSIGNED, IN_PROGRESS, UNAVAILABLE, FAILED }
    record StallTarget(int objectId, int ownerCharacterId, int roomMapId, int x, int y) { }
    record PurchaseStatus(boolean success, String result, int itemId, int quantity, int mesoDelta) { }
}
