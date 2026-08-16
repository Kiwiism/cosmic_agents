package server.agents.economy.scenario;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent reproducible random streams whose state can be checkpointed. */
public final class NamedRandomStreams {
    private final long rootSeed;
    private final Map<String, Stream> streams = new LinkedHashMap<>();

    public NamedRandomStreams(long rootSeed) {
        this.rootSeed = rootSeed;
    }

    public synchronized Stream stream(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("stream name is required");
        return streams.computeIfAbsent(name, key -> new Stream(initialState(rootSeed, key)));
    }

    public synchronized Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        streams.forEach((name, stream) -> result.put(name, stream.state()));
        return Map.copyOf(result);
    }

    public synchronized void restore(Map<String, Long> states) {
        streams.clear();
        states.forEach((name, state) -> streams.put(name, new Stream(state)));
    }

    private static long initialState(long seed, String name) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : name.getBytes(StandardCharsets.UTF_8)) {
            hash ^= Byte.toUnsignedLong(value);
            hash *= 0x100000001b3L;
        }
        return mix(seed ^ hash);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static final class Stream {
        private long state;

        private Stream(long state) {
            this.state = state;
        }

        public synchronized long nextLong() {
            state += 0x9e3779b97f4a7c15L;
            return mix(state);
        }

        public synchronized int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            return (int) Long.remainderUnsigned(nextLong(), bound);
        }

        public synchronized double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        private synchronized long state() {
            return state;
        }
    }
}
