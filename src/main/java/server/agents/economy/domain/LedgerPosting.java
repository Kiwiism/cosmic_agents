package server.agents.economy.domain;

import java.util.Objects;

/** Positive quantity credits an account; negative quantity debits it. */
public record LedgerPosting(LedgerAccount account, AssetKey asset, long quantity, String lotId) {
    public LedgerPosting {
        Objects.requireNonNull(account);
        Objects.requireNonNull(asset);
        if (quantity == 0) throw new IllegalArgumentException("posting quantity cannot be zero");
        lotId = lotId == null ? "" : lotId;
    }
}
