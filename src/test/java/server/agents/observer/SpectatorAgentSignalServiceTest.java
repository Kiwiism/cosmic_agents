package server.agents.observer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorAgentSignalServiceTest {
    @BeforeEach
    void reset() {
        SpectatorAgentSignalService.resetForTests();
    }

    @Test
    void predictsAChangedActiveNavigationDecision() {
        long startedAt = 10_000L;
        SpectatorAgentSignalService.evaluate(
                sample(10, 100, 200, true, true, 12, "walk-to-launch"),
                startedAt);

        List<SpectatorAgentSignalService.Signal> signals =
                SpectatorAgentSignalService.evaluate(
                        sample(10, 100, 200, true, true, 18, "jump-edge"),
                        startedAt + SpectatorAgentSignalService.UPCOMING_COOLDOWN_MS);

        assertEquals(2, signals.size());
        assertTrue(signals.stream().anyMatch(signal ->
                signal.type() == SpectatorInterestService.Type.UPCOMING
                        && signal.detail().contains("jump-edge")));
        assertTrue(signals.stream().anyMatch(signal ->
                signal.type() == SpectatorInterestService.Type.ROUTE
                        && signal.detail().contains("jump-edge")));
    }

    @Test
    void flagsOnlyActiveMovingAgentsAfterSustainedNoProgress() {
        long startedAt = 20_000L;
        SpectatorAgentSignalService.Sample moving =
                sample(20, 50, 75, true, true, 4, "walk");
        SpectatorAgentSignalService.evaluate(moving, startedAt);

        List<SpectatorAgentSignalService.Signal> signals =
                SpectatorAgentSignalService.evaluate(
                        moving,
                        startedAt + SpectatorAgentSignalService.STUCK_AFTER_MS);

        assertTrue(signals.stream().anyMatch(signal ->
                signal.type() == SpectatorInterestService.Type.STUCK));

        SpectatorAgentSignalService.Sample idle =
                sample(21, 50, 75, true, false, -1, "");
        SpectatorAgentSignalService.evaluate(idle, startedAt);
        assertTrue(SpectatorAgentSignalService.evaluate(
                idle,
                startedAt + SpectatorAgentSignalService.STUCK_AFTER_MS * 2
        ).isEmpty());
    }

    @Test
    void movementProgressResetsStuckTimer() {
        long startedAt = 30_000L;
        SpectatorAgentSignalService.evaluate(
                sample(30, 0, 0, true, true, 9, "walk"),
                startedAt);
        SpectatorAgentSignalService.evaluate(
                sample(30, 20, 0, true, true, 9, "walk"),
                startedAt + 10_000L);

        List<SpectatorAgentSignalService.Signal> signals =
                SpectatorAgentSignalService.evaluate(
                        sample(30, 20, 0, true, true, 9, "walk"),
                        startedAt + SpectatorAgentSignalService.STUCK_AFTER_MS);
        assertTrue(signals.stream().noneMatch(signal ->
                signal.type() == SpectatorInterestService.Type.STUCK));
    }

    @Test
    void samplesEachWorldAtMostOncePerInterval() {
        assertTrue(SpectatorAgentSignalService.shouldSampleWorld(0, 1_000L));
        assertFalse(SpectatorAgentSignalService.shouldSampleWorld(
                0,
                1_000L + SpectatorAgentSignalService.SAMPLE_INTERVAL_MS - 1));
        assertTrue(SpectatorAgentSignalService.shouldSampleWorld(
                0,
                1_000L + SpectatorAgentSignalService.SAMPLE_INTERVAL_MS));
        assertTrue(SpectatorAgentSignalService.shouldSampleWorld(1, 1_001L));
    }

    private static SpectatorAgentSignalService.Sample sample(
            int id,
            int x,
            int y,
            boolean active,
            boolean moving,
            int targetRegion,
            String decision) {
        return new SpectatorAgentSignalService.Sample(
                id, 0, 100000000, "Agent" + id,
                x, y, active, moving, targetRegion, decision);
    }
}
