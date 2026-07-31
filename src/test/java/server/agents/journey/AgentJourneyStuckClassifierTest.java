package server.agents.journey;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJourneyStuckClassifierTest {
    @Test
    void detectsOnlyTheRequestedAlternatingMapSuffix() {
        assertTrue(AgentJourneyStuckClassifier.hasAlternatingMapLoop(
                List.of(9, 8, 1, 2, 1, 2, 1, 2, 1, 2), 8));
        assertFalse(AgentJourneyStuckClassifier.hasAlternatingMapLoop(
                List.of(1, 2, 1, 3, 1, 2, 1, 2), 8));
        assertFalse(AgentJourneyStuckClassifier.hasAlternatingMapLoop(
                List.of(1, 1, 1, 1, 1, 1, 1, 1), 8));
    }

    @Test
    void distinguishesLocalOscillationFromTravel() {
        assertTrue(AgentJourneyStuckClassifier.hasLocalPositionOscillation(List.of(
                new Point(100, 100), new Point(120, 100),
                new Point(100, 100), new Point(120, 100),
                new Point(100, 100), new Point(120, 100),
                new Point(100, 100), new Point(120, 100))));
        assertFalse(AgentJourneyStuckClassifier.hasLocalPositionOscillation(List.of(
                new Point(0, 100), new Point(50, 100),
                new Point(100, 100), new Point(150, 100),
                new Point(200, 100), new Point(250, 100),
                new Point(300, 100), new Point(350, 100))));
    }
}
