package tools;

import java.util.Random;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class Randomizer {

    private final static Random rand = new Random();
    private final static ThreadLocal<LongSupplier> scopedSource = new ThreadLocal<>();

    public static <T> T withLongSource(LongSupplier source, Supplier<T> action) {
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

    public static void withLongSource(LongSupplier source, Runnable action) {
        withLongSource(source, () -> { action.run(); return null; });
    }

    public static int nextInt() {
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextInt() : (int) source.getAsLong();
    }

    public static int nextInt(final int arg0) {
        if (arg0 <= 0) throw new IllegalArgumentException("bound must be positive");
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextInt(arg0) : (int) Long.remainderUnsigned(source.getAsLong(), arg0);
    }

    public static void nextBytes(final byte[] bytes) {
        LongSupplier source = scopedSource.get();
        if (source == null) { rand.nextBytes(bytes); return; }
        int index = 0;
        while (index < bytes.length) {
            long value = source.getAsLong();
            for (int offset = 0; offset < Long.BYTES && index < bytes.length; offset++) {
                bytes[index++] = (byte) (value >>> (offset * 8));
            }
        }
    }

    public static boolean nextBoolean() {
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextBoolean() : (source.getAsLong() & 1L) != 0;
    }

    public static double nextDouble() {
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextDouble() : (source.getAsLong() >>> 11) * 0x1.0p-53;
    }

    public static float nextFloat() {
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextFloat() : (source.getAsLong() >>> 40) * 0x1.0p-24f;
    }

    public static long nextLong() {
        LongSupplier source = scopedSource.get();
        return source == null ? rand.nextLong() : source.getAsLong();
    }

    public static int rand(final int lbound, final int ubound) {
        if (ubound < lbound) throw new IllegalArgumentException("upper bound is below lower bound");
        return ((int) (nextDouble() * (ubound - lbound + 1))) + lbound;
    }
}
