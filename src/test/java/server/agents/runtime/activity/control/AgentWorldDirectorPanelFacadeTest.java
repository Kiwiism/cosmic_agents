package server.agents.runtime.activity.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.control.binding.AgentWorldDirectiveRequestCompiler;
import server.agents.runtime.activity.outcome.AgentFileActivityOutcomeInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorJournalStore;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldActivityAdapterCatalog;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorPanelFacadeTest {
    @TempDir Path directory;

    @Test
    void manualPanelValidatesPersistsAndRestoresDirectivesWithoutOwningExecution() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox directives =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentWorldDirectorPanelFacade panel = panel(sessions, directives);
        panel.setMode(27, AgentWorldDirectorMode.MANUAL, "operator", 1_000L);

        AgentWorldDirective invalid = directive("invalid", AgentActivityKind.HUNTING,
                AgentWorldActivityRequestType.FIELD_VISIT, Map.of("mapId", "100000001"));
        assertFalse(panel.preview(invalid, 1_001L).accepted());
        assertThrows(IllegalStateException.class, () -> panel.submit(invalid, 1_001L));

        AgentWorldDirective valid = directive("valid", AgentActivityKind.QUESTING,
                AgentWorldActivityRequestType.AUTHORED_PLAN, Map.of());
        assertTrue(panel.preview(valid, 1_002L).accepted());
        panel.submit(valid, 1_002L);

        AgentWorldDirectorPanelView restored = panel(sessions, directives).view(27, 10);
        assertEquals(AgentWorldDirectorMode.MANUAL, restored.control().session().mode());
        assertEquals(AgentWorldDirectiveStatus.PENDING,
                restored.control().directives().getFirst().status());
        assertEquals(5, restored.activityCoverage().size());
    }

    @Test
    void rejectsAggregateTargetsUntilTheirAdmissionOwnerIsConnected() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox directives =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentWorldDirectorPanelFacade panel = panel(sessions, directives);
        panel.setMode(27, AgentWorldDirectorMode.MANUAL, "operator", 1_000L);
        AgentWorldDirective commerce = directive("commerce", AgentActivityKind.COMMERCE,
                AgentWorldActivityRequestType.COMMERCE_VISIT, Map.of());

        AgentWorldDirectivePreview preview = panel.preview(commerce, 1_001L);

        assertFalse(preview.accepted());
        assertTrue(preview.reason().contains("aggregate admission"));
        assertTrue(directives.list(27).isEmpty());
    }

    @Test
    void acceptsKpqLobbyDirectiveNowThatItsAdmissionOwnerIsConnected() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox directives =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentWorldDirectorPanelFacade panel = panel(sessions, directives);
        panel.setMode(27, AgentWorldDirectorMode.MANUAL, "operator", 1_000L);
        AgentWorldDirective kpq = directive("kpq", AgentActivityKind.PARTY_QUEST,
                AgentWorldActivityRequestType.PARTY_QUEST_VISIT, Map.of(
                        "scenarioId", "kpq", "partySize", "4", "maximumRuns", "1"));

        assertTrue(panel.preview(kpq, 1_001L).accepted());
    }

    private AgentWorldDirectorPanelFacade panel(
            AgentFileWorldDirectorSessionStore sessions,
            AgentFileWorldDirectiveInbox directives) {
        return new AgentWorldDirectorPanelFacade(
                new AgentWorldDirectorControlService(sessions, directives),
                new AgentFileActivityOutcomeInbox(directory.resolve("outcomes")),
                new AgentFileWorldDirectorJournalStore(directory.resolve("journal")),
                new AgentWorldDirectiveRequestCompiler(), AgentWorldActivityAdapterCatalog.current());
    }

    private AgentWorldDirective directive(
            String id,
            AgentActivityKind kind,
            AgentWorldActivityRequestType requestType,
            Map<String, String> parameters) {
        return new AgentWorldDirective(1, id, 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, kind, requestType, "test-plan",
                parameters, AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 10, 1_000L, 0L, "test");
    }
}
