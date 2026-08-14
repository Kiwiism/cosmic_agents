package server.economy;

import java.util.Objects;
import java.util.function.Supplier;

/** Lexically scoped attribution passed through existing Cosmic transaction entry points. */
public final class EconomyOperationContext {
    private static final ThreadLocal<EconomyOperationMetadata> CURRENT = new ThreadLocal<>();
    private EconomyOperationContext() { }

    public static void with(EconomyOperationMetadata metadata, Runnable action) {
        with(metadata, () -> { action.run(); return null; });
    }

    public static <T> T with(EconomyOperationMetadata metadata, Supplier<T> action) {
        Objects.requireNonNull(metadata); Objects.requireNonNull(action);
        EconomyOperationMetadata previous = CURRENT.get();
        CURRENT.set(metadata);
        try { return action.get(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    static EconomyOperationMetadata current() {
        EconomyOperationMetadata value = CURRENT.get();
        return value == null ? EconomyOperationMetadata.unattributed() : value;
    }

    public static EconomyOperationMetadata currentMetadata() { return current(); }
}
