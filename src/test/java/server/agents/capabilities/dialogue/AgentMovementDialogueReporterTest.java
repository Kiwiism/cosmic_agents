package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementDialogueReporterTest {
    @Test
    void shouldBuildMovementReportWithoutMapMetrics() {
        AgentMovementDialogueReporter.MovementProfile profile =
                new AgentMovementDialogueReporter.MovementProfile(120, 110, 150.5d, 1.2d, 5.5d, 4.5d, 42.0d);

        List<String> report = AgentMovementDialogueReporter.movementStatsReport(profile, 120, 110, false, 3, null);

        assertEquals(List.of(
                "speed 120% jump 110%",
                "walk 150.5 px/s, hforce 1.2, climb 3 px/tick",
                "jump 5.5/tick, rope 4.5/tick, max jump 42.0 px"
        ), report);
    }

    @Test
    void shouldBuildMovementReportWithMapMetrics() {
        AgentMovementDialogueReporter.MovementProfile profile =
                new AgentMovementDialogueReporter.MovementProfile(120, 110, 150.5d, 1.2d, 5.5d, 4.5d, 42.0d);
        AgentMovementDialogueReporter.MapMovementProfile mapProfile =
                new AgentMovementDialogueReporter.MapMovementProfile(7, 3, 123, 234);

        List<String> report = AgentMovementDialogueReporter.movementStatsReport(
                profile, 120, 110, false, 3, mapProfile);

        assertEquals(List.of(
                "speed 120% jump 110%",
                "walk 150.5 px/s, 7 px/tick, climb 3, hforce 1.2",
                "jump 5.5, rope 4.5, max 42.0 px, reach 123/234 px"
        ), report);
    }

    @Test
    void shouldReportForcedMovementStats() {
        AgentMovementDialogueReporter.MovementProfile profile =
                new AgentMovementDialogueReporter.MovementProfile(100, 100, 125.0d, 1.0d, 5.0d, 4.0d, 40.0d);

        List<String> report = AgentMovementDialogueReporter.movementStatsReport(profile, 140, 125, true, 3, null);

        assertEquals("speed 100% jump 100% (map forced; raw 140%/125%)", report.getFirst());
    }

    @Test
    void shouldReportUnavailableMovementStats() {
        assertEquals(List.of(AgentDialogueCatalog.movementStatsUnavailableReply()),
                AgentMovementDialogueReporter.movementStatsReport(null, 0, 0, false, 0, null));
    }
}
