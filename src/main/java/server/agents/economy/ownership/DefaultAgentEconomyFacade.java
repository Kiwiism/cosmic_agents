package server.agents.economy.ownership;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultAgentEconomyFacade implements AgentEconomyFacade {
    private static final Duration AUTHORIZATION_TTL = Duration.ofHours(6);
    private final UUID runId;
    private final LegacyDispositionEvaluator legacy;
    private final ShadowEconomyEvaluator shadow;
    private final EconomyOwnershipJournal journal;
    private final Map<Integer, AgentState> states = new ConcurrentHashMap<>();

    public DefaultAgentEconomyFacade(UUID runId, ShadowEconomyEvaluator shadow,
                                     EconomyOwnershipJournal journal) {
        this.runId = Objects.requireNonNull(runId); this.shadow = Objects.requireNonNull(shadow);
        this.journal = Objects.requireNonNull(journal); this.legacy = new LegacyDispositionEvaluator();
    }

    @Override
    public InventoryReview protectAtFreeMarketEntry(String agentId, InventorySnapshot snapshot,
                                                     Instant logicalAt) {
        List<InventoryDispositionDecision> decisions = snapshot.items().stream().map(item ->
                new InventoryDispositionDecision(item.ref(), item.quantity(),
                        InventoryDispositionDecision.Disposition.PROTECTED_UNREVIEWED,
                        "Awaiting FM market appraisal", "NONE", "KEEP_FOR_MARKET_REVIEW", false)).toList();
        InventoryReview review = new InventoryReview(UUID.randomUUID(), runId, agentId, snapshot,
                logicalAt, InventoryReview.Purpose.FM_ENTRY_SCAN, decisions, List.of(), List.of());
        states.put(snapshot.characterId(), new AgentState(agentId, snapshot.revision(), new HashMap<>()));
        journal.appendReview(review);
        return review;
    }

    @Override
    public InventoryReview appraise(String agentId, InventorySnapshot snapshot,
                                    List<LegacyDispositionProposal> proposals, Instant logicalAt) {
        List<InventoryDispositionDecision> decisions = legacy.evaluate(snapshot, proposals, shadow);
        List<InventoryReview.AssetReservation> reservations = new ArrayList<>();
        List<InventoryReview.ActionAuthorization> authorizations = new ArrayList<>();
        Map<UUID, AuthorizationState> active = new HashMap<>();
        Instant expiresAt = logicalAt.plus(AUTHORIZATION_TTL);
        for (InventoryDispositionDecision decision : decisions) {
            if (decision.disposition() != InventoryDispositionDecision.Disposition.NPC_SALE_AUTHORIZED
                    && decision.disposition() != InventoryDispositionDecision.Disposition.PLAYER_SHOP_LISTING_RESERVED)
                continue;
            String action = decision.disposition() == InventoryDispositionDecision.Disposition.NPC_SALE_AUTHORIZED
                    ? "SELL_TO_NPC" : "LIST_IN_PLAYER_SHOP";
            LegacyDispositionProposal proposal = proposals.stream()
                    .filter(value -> value.item().equals(decision.item()) && value.action().name().equals(action))
                    .findFirst().orElseThrow();
            reservations.add(new InventoryReview.AssetReservation(UUID.randomUUID(), decision.item(),
                    decision.quantity(), action, proposal.venue()));
            if ("SELL_TO_NPC".equals(action)) {
                InventoryReview.ActionAuthorization authorization = new InventoryReview.ActionAuthorization(
                        UUID.randomUUID(), decision.item(), decision.quantity(), action, proposal.venue(),
                        snapshot.revision(), expiresAt);
                authorizations.add(authorization);
                active.put(authorization.authorizationId(), new AuthorizationState(authorization, false));
            }
        }
        InventoryReview review = new InventoryReview(UUID.randomUUID(), runId, agentId, snapshot,
                logicalAt, InventoryReview.Purpose.FM_MARKET_APPRAISAL, decisions,
                reservations, authorizations);
        states.put(snapshot.characterId(), new AgentState(agentId, snapshot.revision(), active));
        journal.appendReview(review);
        return review;
    }

    @Override
    public synchronized NpcSalePermit claimNpcSale(String agentId, InventorySnapshot current,
                                                   InventoryItemRef item, int quantity, String venue,
                                                   Instant logicalAt) {
        AgentState state = states.get(current.characterId());
        if (state == null || !state.agentId.equals(agentId))
            return denied(agentId, current, item, quantity, venue, logicalAt, "PROTECTED_UNREVIEWED");
        Optional<InventoryItemSnapshot> physical = current.find(item.inventoryType(), item.slot(), item.itemId());
        if (physical.isEmpty() || !physical.orElseThrow().ref().fingerprint().equals(item.fingerprint())
                || physical.orElseThrow().quantity() < quantity)
            return denied(agentId, current, item, quantity, venue, logicalAt, "STALE_OR_MISSING_ITEM");
        AuthorizationState match = state.authorizations.values().stream()
                .filter(value -> !value.consumed && !logicalAt.isAfter(value.authorization.expiresAt()))
                .filter(value -> value.authorization.action().equals("SELL_TO_NPC"))
                .filter(value -> value.authorization.venue().equals(venue)
                        || value.authorization.venue().equals("NPC_ANYWHERE"))
                .filter(value -> value.authorization.item().equals(item))
                .filter(value -> value.authorization.quantity() >= quantity).findFirst().orElse(null);
        if (match == null)
            return denied(agentId, current, item, quantity, venue, logicalAt, "NO_ACTIVE_AUTHORIZATION");
        match.consumed = true;
        journal.markAuthorizationConsumed(match.authorization.authorizationId(), logicalAt);
        journal.appendGuardEvent(runId, agentId, current.characterId(), logicalAt, "SELL_TO_NPC",
                item, quantity, true, "AUTHORIZED", match.authorization.authorizationId());
        return new NpcSalePermit(true, "AUTHORIZED", match.authorization.authorizationId());
    }

    private NpcSalePermit denied(String agentId, InventorySnapshot current, InventoryItemRef item,
                                 int quantity, String venue, Instant at, String reason) {
        journal.appendGuardEvent(runId, agentId, current.characterId(), at, "SELL_TO_NPC",
                item, quantity, false, reason + "@" + venue, null);
        return NpcSalePermit.denied(reason);
    }

    private record AgentState(String agentId, String reviewedRevision,
                              Map<UUID, AuthorizationState> authorizations) { }
    private static final class AuthorizationState {
        private final InventoryReview.ActionAuthorization authorization;
        private boolean consumed;
        private AuthorizationState(InventoryReview.ActionAuthorization authorization, boolean consumed) {
            this.authorization = authorization; this.consumed = consumed;
        }
    }
}
