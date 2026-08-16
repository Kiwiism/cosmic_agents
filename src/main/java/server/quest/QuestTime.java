package server.quest;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Scoped quest clock; player sessions use wall time and logical simulations can replay exact instants. */
public final class QuestTime {
    private static final ThreadLocal<LongSupplier> scopedSource = new ThreadLocal<>();

    private QuestTime() { }

    public static long now() {
        LongSupplier source = scopedSource.get();
        return source == null ? System.currentTimeMillis() : source.getAsLong();
    }

    public static <T> T withTimeSource(LongSupplier source, Supplier<T> action) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(action);
        LongSupplier previous = scopedSource.get();
        scopedSource.set(source);
        try {
            return action.get();
        } finally {
            if (previous == null) scopedSource.remove(); else scopedSource.set(previous);
        }
    }

    public static void withTimeSource(LongSupplier source, Runnable action) {
        withTimeSource(source, () -> { action.run(); return null; });
    }
}
