package server.agents.economy.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-agent memory only. It has no access to administrative projections or unvisited rooms. */
public final class PrivateMarketKnowledge {
    private final Map<Integer, List<MarketObservation>> observations = new HashMap<>();

    public synchronized void observe(MarketObservation observation) {
        observations.computeIfAbsent(observation.itemId(), ignored -> new ArrayList<>()).add(observation);
    }

    public synchronized List<MarketObservation> recentFor(int itemId, Instant now, Duration memory) {
        Instant threshold = now.minus(memory);
        return observations.getOrDefault(itemId, List.of()).stream()
                .filter(observation -> !observation.observedAt().isBefore(threshold))
                .sorted(Comparator.comparing(MarketObservation::observedAt))
                .toList();
    }

    public synchronized long observedMedianAsk(int itemId, Instant now, Duration memory) {
        List<Long> prices = recentFor(itemId, now, memory).stream()
                .filter(observation -> observation.state() == MarketObservation.State.LISTED)
                .map(MarketObservation::unitPrice).sorted().toList();
        if (prices.isEmpty()) return 0;
        int middle = prices.size() / 2;
        return prices.size() % 2 == 1 ? prices.get(middle)
                : Math.addExact(prices.get(middle - 1), prices.get(middle)) / 2;
    }
}
