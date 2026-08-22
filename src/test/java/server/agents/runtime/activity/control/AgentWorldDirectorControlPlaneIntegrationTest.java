package server.agents.runtime.activity.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentWorldDirectorControlPlaneIntegrationTest {
    @TempDir Path directory;

    @Test
    void manualIntentSurvivesRestartWhileLiveOwnershipRemainsDisabled() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentWorldDirectorControlService control =
                new AgentWorldDirectorControlService(sessions, inbox);
        control.setMode(27, AgentWorldDirectorMode.MANUAL, "integration", 1_000L);
        control.submit(directive(), 1_001L);

        AgentWorldDirectorControlService restarted = new AgentWorldDirectorControlService(
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions")),
                new AgentFileWorldDirectiveInbox(directory.resolve("directives")));
        AgentWorldControlStatus status = restarted.status(27);

        assertEquals(AgentWorldDirectorMode.MANUAL, status.session().mode());
        assertEquals("manual-integration", status.directives().getFirst()
                .directive().directiveId());
        assertFalse(status.session().mayOwnActivity());
    }

    private static AgentWorldDirective directive() {
        return new AgentWorldDirective(1, "manual-integration", 27,
                AgentWorldDirectiveType.START_ACTIVITY, AgentWorldDirectiveSource.OPERATOR,
                null, AgentActivityKind.QUESTING, AgentWorldActivityRequestType.AUTHORED_PLAN,
                "victoria-level15-mvp", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 10, 1_000L, 0L, "test");
    }
}
