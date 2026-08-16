package server.agents.economy.decision;

import server.agents.economy.integration.cosmic.CosmicMarketObservationService;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.session.CommerceParticipant;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Chooses only from offers this agent physically observed during the current trip. */
public final class ObservedPurchasePolicy {
    public Optional<Decision> choose(List<CosmicMarketObservationService.ObservedOffer> offers,
                                     List<AgentNeed> needs, CommerceParticipant profile, long mesos) {
        long liquidityBudget = Math.max(0, Math.round(mesos * (1d - profile.liquidityPreference())));
        return offers.stream().flatMap(offer -> needs.stream()
                        .filter(need -> matches(need, offer.observation().itemId()) && need.deficit() > 0)
                        .map(need -> candidate(offer, need, liquidityBudget)))
                .filter(candidate -> candidate != null)
                .max(Comparator.comparingDouble(Candidate::score)
                        .thenComparing(candidate -> candidate.offer().observation().observationId()))
                .map(candidate -> new Decision(candidate.offer(), candidate.bundles(),
                        candidate.need().reason(), candidate.totalPrice(), candidate.score(),
                        "observed=" + candidate.offer().observation().observationId()
                                + " deficit=" + candidate.need().deficit()
                                + " wtp=" + candidate.need().maximumWillingnessToPay()
                                + " evidence=" + candidate.need().evidence()));
    }

    private static Candidate candidate(CosmicMarketObservationService.ObservedOffer offer,
                                       AgentNeed need, long liquidityBudget) {
        var listing = offer.observation();
        int bundles = Math.min(listing.bundles(), Math.max(1,
                (need.deficit() + listing.quantityPerBundle() - 1) / listing.quantityPerBundle()));
        long total;
        try { total = Math.multiplyExact(listing.bundlePrice(), bundles); }
        catch (ArithmeticException overflow) { return null; }
        long willingness = need.maximumWillingnessToPay();
        if (willingness <= 0 || total > willingness || total > liquidityBudget || bundles > Short.MAX_VALUE)
            return null;
        double surplus = (willingness - total) / (double) Math.max(1, willingness);
        return new Candidate(offer, need, (short) bundles, total, need.urgency() * 2d + surplus);
    }

    private static boolean matches(AgentNeed need, int itemId) {
        return need.itemId() == itemId || need.substitutes().contains(itemId);
    }

    private record Candidate(CosmicMarketObservationService.ObservedOffer offer, AgentNeed need,
                             short bundles, long totalPrice, double score) { }
    public record Decision(CosmicMarketObservationService.ObservedOffer offer, short bundles,
                           EconomicReason reason, long totalPrice, double score, String evidence) { }
}
