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
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorControlServiceTest {
    @TempDir Path directory;

    @Test
    void manualModePreviewsSubmitsAndCancelsOperatorDirective() {
        AgentWorldDirectorControlService service = service();
        service.setMode(27, AgentWorldDirectorMode.MANUAL, "operator test", 1_000L);
        AgentWorldDirective directive = directive(AgentWorldDirectiveSource.OPERATOR);

        assertTrue(service.preview(directive, 1_001L).accepted());
        service.submit(directive, 1_001L);
        assertEquals(1, service.status(27).directives().size());
        assertEquals(AgentWorldDirectiveStatus.CANCELLED,
                service.cancel(27, "manual-1", "operator cancelled", 1_002L).status());
    }

    @Test
    void manualModeRejectsPolicyDirectiveAndObserveRejectsExecution() {
        AgentWorldDirectorControlService service = service();
        service.setMode(27, AgentWorldDirectorMode.MANUAL, "manual", 1_000L);
        assertFalse(service.preview(directive(AgentWorldDirectiveSource.POLICY), 1_001L)
                .accepted());
        service.setMode(27, AgentWorldDirectorMode.OBSERVE, "observe", 1_002L);
        assertThrows(IllegalStateException.class,
                () -> service.submit(directive(AgentWorldDirectiveSource.OPERATOR), 1_003L));
    }

    private AgentWorldDirectorControlService service() {
        return new AgentWorldDirectorControlService(
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions")),
                new AgentFileWorldDirectiveInbox(directory.resolve("directives")));
    }

    private static AgentWorldDirective directive(AgentWorldDirectiveSource source) {
        return new AgentWorldDirective(1, "manual-1", 27,
                AgentWorldDirectiveType.START_ACTIVITY, source, null,
                AgentActivityKind.QUESTING, AgentWorldActivityRequestType.INDIVIDUAL_QUEST,
                "quest:1001", Map.of(), AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 100, 1_000L, 0L, "test");
    }
}
