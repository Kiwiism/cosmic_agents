package server.agents.economy.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable evidence envelope. Asset postings must balance exactly for every asset key. */
public record EconomicEvent(
        UUID eventId,
        UUID runId,
        Instant logicalTime,
        EconomicEventKind kind,
        String idempotencyKey,
        String causationId,
        String correlationId,
        String configHash,
        String catalogVersion,
        List<String> actorIds,
        Map<String, Object> evidence,
        List<LedgerPosting> postings
) {
    public EconomicEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(logicalTime);
        Objects.requireNonNull(kind);
        requireText(idempotencyKey, "idempotencyKey");
        requireText(configHash, "configHash");
        requireText(catalogVersion, "catalogVersion");
        causationId = causationId == null ? "" : causationId;
        correlationId = correlationId == null ? "" : correlationId;
        actorIds = actorIds == null ? List.of() : List.copyOf(actorIds);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        postings = postings == null ? List.of() : List.copyOf(postings);
        validateBalanced(postings);
    }

    private static void validateBalanced(List<LedgerPosting> postings) {
        Map<AssetKey, Long> balances = new LinkedHashMap<>();
        for (LedgerPosting posting : postings) {
            balances.merge(posting.asset(), posting.quantity(), Math::addExact);
        }
        List<AssetKey> unbalanced = new ArrayList<>();
        balances.forEach((asset, quantity) -> { if (quantity != 0) unbalanced.add(asset); });
        if (!unbalanced.isEmpty()) throw new IllegalArgumentException("Unbalanced assets: " + unbalanced);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
