package server.agents.runtime.activity.control;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.binding.AgentWorldActivityBinding;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorRolloutGateResult;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentFileActivityHandoffStore;
import server.agents.runtime.activity.session.AgentPersistentActivityHandoffCoordinator;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorldDirectiveProcessorTest {
    @TempDir Path directory;

    @Test
    void claimsAndCompletesDirectAdmissionDurably() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L));
        inbox.submit(directive(), 1_000L);
        AgentWorldDirectiveProcessor processor = processor(sessions, inbox);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);

        AgentWorldDirectiveProcessor.Result result = processor.tick(
                mock(AgentRuntimeEntry.class), agent, null, "", 1_001L);

        assertEquals(AgentWorldDirectiveProcessor.Result.Status.COMPLETED, result.status());
        assertEquals(AgentWorldDirectiveStatus.COMPLETED,
                inbox.load(27, "start-quest").orElseThrow().status());
        assertTrue(sessions.load(27).orElseThrow().mayOwnActivity());
    }

    @Test
    void disabledExecutionGateLeavesDirectivePending() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L));
        inbox.submit(directive(), 1_000L);
        AgentWorldDirectiveProcessor processor = new AgentWorldDirectiveProcessor(
                sessions, inbox,
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory.resolve("handoffs"))),
                (directive, entry, agent, sourceKind, sourceSessionId) -> binding(),
                AgentWorldDirectiveExecutionGate.disabled(), 60_000L);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);

        assertEquals(AgentWorldDirectiveProcessor.Result.Status.BLOCKED,
                processor.tick(mock(AgentRuntimeEntry.class), agent, null, "", 1_001L).status());
        assertEquals(AgentWorldDirectiveStatus.PENDING,
                inbox.load(27, "start-quest").orElseThrow().status());
    }

    private AgentWorldDirectiveProcessor processor(
            AgentFileWorldDirectorSessionStore sessions, AgentFileWorldDirectiveInbox inbox) {
        return new AgentWorldDirectiveProcessor(sessions, inbox,
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory.resolve("handoffs"))),
                (directive, entry, agent, sourceKind, sourceSessionId) -> binding(),
                (session, directive, entry, agent, nowMs) ->
                        AgentWorldDirectorRolloutGateResult.allow("test gate"),
                60_000L);
    }

    private static AgentWorldActivityBinding binding() {
        AgentActivitySessionSnapshot idle =
                AgentActivitySessionSnapshot.idle(AgentActivityKind.TOWN_LIFE, "27");
        AgentActivitySessionSnapshot active = new AgentActivitySessionSnapshot(
                AgentActivityKind.QUESTING, AgentActivityPhase.ACTIVE, "quest-session",
                "start-quest", "world-director", "27", 1_001L, "");
        return new AgentWorldActivityBinding(
                new server.agents.runtime.activity.session.AgentActivitySourcePort() {
                    @Override public AgentActivitySessionSnapshot snapshot(long nowMs) { return idle; }
                    @Override public server.agents.runtime.activity.session.AgentActivityExitResult
                            requestGracefulExit(String reason, long nowMs, long deadlineMs) {
                        return server.agents.runtime.activity.session.AgentActivityExitResult.released(reason);
                    }
                },
                (agentId, kind, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed(),
                nowMs -> server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready(),
                nowMs -> AgentActivityAdmissionResult.accepted(active),
                (sessionId, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityRollbackPort.Result.resumed("test"),
                nowMs -> null);
    }

    private static AgentWorldDirective directive() {
        return new AgentWorldDirective(1, "start-quest", 27,
                AgentWorldDirectiveType.START_ACTIVITY, AgentWorldDirectiveSource.OPERATOR,
                null, AgentActivityKind.QUESTING, AgentWorldActivityRequestType.AUTHORED_PLAN,
                "victoria-level15-mvp", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 10, 1_000L, 0L, "test");
    }
}
