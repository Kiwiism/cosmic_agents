package server.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedStepAccumulatorTest {
    @Test
    void preservesRemainderAcrossOuterTicks() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(8);
        assertEquals(6, accumulator.accumulate(50_000_000L, 12).steps());
        FixedStepAccumulator.StepBatch next = accumulator.accumulate(50_000_000L, 12);
        assertEquals(6, next.steps());
        assertEquals(4_000_000L, next.leftoverNanos());
        assertFalse(next.capped());
    }

    @Test
    void capsCatchUpAndDropsUnboundedBacklog() {
        FixedStepAccumulator.StepBatch batch =
                new FixedStepAccumulator(8).accumulate(2_000_000_000L, 10);
        assertEquals(10, batch.steps());
        assertEquals(0, batch.leftoverNanos());
        assertTrue(batch.capped());
    }
}
