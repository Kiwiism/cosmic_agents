package tools;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RandomizerScopedSourceTest {
    @Test
    void scopesDeterministicGameplayRandomnessWithoutChangingTheGlobalDefault() {
        AtomicLong values = new AtomicLong(10);
        int first = Randomizer.withLongSource(values::getAndIncrement, () -> Randomizer.rand(1, 100));
        values.set(10);
        int replay = Randomizer.withLongSource(values::getAndIncrement, () -> Randomizer.rand(1, 100));

        assertEquals(first, replay);
        assertDoesNotThrow(Randomizer::nextLong);
    }

    @Test
    void drivesFloatingPointProbabilityRollsFromTheScopedStream() {
        assertEquals(0.0, Randomizer.withLongSource(() -> 0L, Randomizer::nextDouble));
        assertTrue(Randomizer.withLongSource(() -> -1L, Randomizer::nextDouble) > 0.999);
    }
}
