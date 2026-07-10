package net.server.channel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShutdownStageRunnerTest {
    @Test
    void continuesAfterAnIndependentStageFails() {
        AtomicInteger completed = new AtomicInteger();
        List<String> failures = new ArrayList<>();

        ShutdownStageRunner.run(List.of(
                new ShutdownStageRunner.Stage("first", completed::incrementAndGet),
                new ShutdownStageRunner.Stage("broken", () -> { throw new IllegalStateException("boom"); }),
                new ShutdownStageRunner.Stage("last", completed::incrementAndGet)),
                (stage, error) -> failures.add(stage));

        assertEquals(2, completed.get());
        assertEquals(List.of("broken"), failures);
    }
}
