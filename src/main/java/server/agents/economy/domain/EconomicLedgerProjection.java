package server.agents.economy.domain;

import java.util.HashMap;
import java.util.Map;

/** Replayable holdings projection with non-negative agent and escrow invariants. */
public final class EconomicLedgerProjection {
    private final Map<HoldingKey, Long> balances = new HashMap<>();

    public synchronized void apply(EconomicEvent event) {
        Map<HoldingKey, Long> candidate = new HashMap<>(balances);
        for (LedgerPosting posting : event.postings()) {
            HoldingKey key = new HoldingKey(posting.account(), posting.asset(), posting.lotId());
            candidate.merge(key, posting.quantity(), Math::addExact);
        }
        candidate.forEach((key, balance) -> {
            if (("AGENT".equals(key.account().type()) || "ESCROW".equals(key.account().type()))
                    && balance < 0) throw new IllegalStateException("Negative holding: " + key);
        });
        balances.clear();
        balances.putAll(candidate);
    }

    public synchronized long balance(LedgerAccount account, AssetKey asset, String lotId) {
        return balances.getOrDefault(new HoldingKey(account, asset, lotId == null ? "" : lotId), 0L);
    }

    public synchronized Map<HoldingKey, Long> snapshot() { return Map.copyOf(balances); }

    public record HoldingKey(LedgerAccount account, AssetKey asset, String lotId) { }
}
