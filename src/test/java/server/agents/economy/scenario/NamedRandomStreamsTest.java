package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamedRandomStreamsTest {
    @Test
    void streamsAreIndependentAndCheckpointable() {
        NamedRandomStreams streams = new NamedRandomStreams(42);
        long lootFirst = streams.stream("loot").nextLong();
        streams.stream("ambient").nextLong();
        long lootSecond = streams.stream("loot").nextLong();

        NamedRandomStreams comparison = new NamedRandomStreams(42);
        assertEquals(lootFirst, comparison.stream("loot").nextLong());
        assertEquals(lootSecond, comparison.stream("loot").nextLong());

        var checkpoint = streams.snapshot();
        long expected = streams.stream("loot").nextLong();
        NamedRandomStreams restored = new NamedRandomStreams(999);
        restored.restore(checkpoint);
        assertEquals(expected, restored.stream("loot").nextLong());
    }
}
