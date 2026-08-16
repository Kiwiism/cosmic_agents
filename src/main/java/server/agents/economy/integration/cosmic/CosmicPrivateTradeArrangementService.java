package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.agents.economy.market.PrivateTradeArrangement;
import server.agents.economy.persistence.StallOfferStore;
import server.agents.economy.session.CommerceParticipant;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Buyer-driven rendezvous and fingerprint-exact settlement for an accepted public stall offer. */
public final class CosmicPrivateTradeArrangementService
        implements AutonomousFreeMarketBehavior.ArrangementBehavior {
    private final UUID runId;
    private final StallOfferStore arrangements;
    private final EconomyParticipantRegistry participants;
    private final FreeMarketPhysicalGateway physical;
    private final CosmicMarketSellerGateway sellerGateway;
    private final CosmicNegotiatedTradeExecutor trades;

    public CosmicPrivateTradeArrangementService(UUID runId, StallOfferStore arrangements,
                                                EconomyParticipantRegistry participants,
                                                FreeMarketPhysicalGateway physical,
                                                CosmicMarketSellerGateway sellerGateway,
                                                CosmicNegotiatedTradeExecutor trades) {
        this.runId = Objects.requireNonNull(runId); this.arrangements = Objects.requireNonNull(arrangements);
        this.participants = Objects.requireNonNull(participants); this.physical = Objects.requireNonNull(physical);
        this.sellerGateway = Objects.requireNonNull(sellerGateway); this.trades = Objects.requireNonNull(trades);
    }

    @Override
    public Result progress(Character buyer, CommerceParticipant profile, Instant logicalAt) {
        PrivateTradeArrangement agreement = arrangements.pendingArrangementForBuyer(
                runId, profile.agentId(), logicalAt).orElse(null);
        if (agreement == null) return Result.none();
        if (!logicalAt.isBefore(agreement.expiresAt())) {
            arrangements.resolveArrangement(agreement.arrangementId(),
                    PrivateTradeArrangement.Status.EXPIRED, logicalAt, null, "MEETUP_TIMEOUT");
            return new Result(true, true, false, agreement.arrangementId().toString(), "EXPIRED",
                    agreement.itemId(), Map.of("expiresAt", agreement.expiresAt().toString()));
        }
        CosmicPublicTradeNegotiator.Participant seller = participants
                .byLogicalId(agreement.sellerAgentId()).orElse(null);
        if (seller == null) return pending(agreement, "SELLER_NOT_IN_ECONOMY", false);
        FreeMarketPhysicalGateway.ActionStatus travel = physical.requestRoom(buyer, agreement.roomMapId());
        if (travel != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return pending(agreement, "TRAVEL_" + travel.name(), true);
        FreeMarketPhysicalGateway.StallTarget target = physical.visibleStalls(buyer).stream()
                .filter(value -> value.ownerCharacterId() == seller.character().getId()).findFirst().orElse(null);
        if (target == null) {
            arrangements.resolveArrangement(agreement.arrangementId(),
                    PrivateTradeArrangement.Status.CANCELLED_LISTING_CHANGED, logicalAt, null,
                    "SELLER_STALL_NOT_AVAILABLE");
            return new Result(true, true, false, agreement.arrangementId().toString(),
                    "CANCELLED_LISTING_CHANGED", agreement.itemId(), Map.of());
        }
        FreeMarketPhysicalGateway.ActionStatus approach = physical.requestApproach(buyer, target);
        if (approach != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return pending(agreement, "APPROACH_" + approach.name(), true);
        if (buyer.getMeso() < agreement.agreedMesos()) {
            arrangements.resolveArrangement(agreement.arrangementId(),
                    PrivateTradeArrangement.Status.CANCELLED_PARTICIPANT, logicalAt, null,
                    "BUYER_FUNDS_CHANGED");
            return new Result(true, true, false, agreement.arrangementId().toString(),
                    "BUYER_FUNDS_CHANGED", agreement.itemId(), Map.of());
        }
        if (!sellerGateway.close(seller.character(), "ACCEPTED_OFFER_EXACT_TRADE"))
            return pending(agreement, "SELLER_STALL_CLOSE_PENDING", true);
        var execution = trades.executeExactItem(agreement.arrangementId().toString(),
                agreement.buyerAgentId(), agreement.agreedMesos(), agreement.sellerAgentId(),
                agreement.itemId(), agreement.itemFingerprint(), agreement.quantity());
        arrangements.resolveArrangement(agreement.arrangementId(), execution.succeeded()
                        ? PrivateTradeArrangement.Status.EXECUTED
                        : PrivateTradeArrangement.Status.CANCELLED_LISTING_CHANGED,
                logicalAt, execution.transactionId().isBlank() ? null : execution.transactionId(),
                execution.evidence());
        return new Result(true, true, false, agreement.arrangementId().toString(),
                execution.succeeded() ? "EXECUTED" : "SETTLEMENT_FAILED", agreement.itemId(),
                Map.of("transactionId", execution.transactionId(), "evidence", execution.evidence()));
    }

    private static Result pending(PrivateTradeArrangement value, String outcome, boolean externalPending) {
        return new Result(true, false, externalPending, value.arrangementId().toString(), outcome,
                value.itemId(), Map.of("sellerId", value.sellerAgentId(), "room", value.roomMapId()));
    }
}
