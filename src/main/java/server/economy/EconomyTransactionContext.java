package server.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-operation enlistment context for non-character state such as market escrow. */
public final class EconomyTransactionContext {
    private final List<EconomyAtomicPersistence> persistence = new ArrayList<>();
    private final List<Runnable> rollback = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();

    public void enlist(EconomyAtomicPersistence durablePersistence, Runnable inMemoryRollback) {
        persistence.add(Objects.requireNonNull(durablePersistence));
        rollback.add(Objects.requireNonNull(inMemoryRollback));
    }

    List<EconomyAtomicPersistence> persistence() {
        return List.copyOf(persistence);
    }

    public void recordEvidence(String key, Object value) {
        if (key == null || key.isBlank() || value == null || evidence.putIfAbsent(key, value) != null)
            throw new IllegalArgumentException("operation evidence keys must be unique and non-null");
    }

    Map<String, Object> evidence() { return Map.copyOf(evidence); }

    void rollback() {
        List<Runnable> reverse = new ArrayList<>(rollback);
        Collections.reverse(reverse);
        for (Runnable action : reverse) action.run();
    }
}
