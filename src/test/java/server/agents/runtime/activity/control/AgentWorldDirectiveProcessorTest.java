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
import server.agents.runtime.activity.world.AgentWorldDirectorPhase;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void retainsSuspendedSourceUntilDurableHandoffCompletes() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentFileActivityHandoffStore handoffStore =
                new AgentFileActivityHandoffStore(directory.resolve("handoffs"));
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L));
        inbox.submit(directive(), 1_000L);
        AtomicReference<AgentActivitySessionSnapshot> source = new AtomicReference<>(
                new AgentActivitySessionSnapshot(AgentActivityKind.HUNTING,
                        AgentActivityPhase.ACTIVE, "field-session", "hunt",
                        "test", "27", 1_000L, ""));
        AgentWorldDirectiveProcessor processor = new AgentWorldDirectiveProcessor(
                sessions, inbox,
                new AgentPersistentActivityHandoffCoordinator(handoffStore),
                (directive, entry, agent, sourceKind, sourceSessionId) -> handoffBinding(source),
                (session, directive, entry, agent, nowMs) ->
                        AgentWorldDirectorRolloutGateResult.allow("test gate"),
                60_000L);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);

        assertEquals(AgentWorldDirectiveProcessor.Result.Status.PROGRESSED,
                processor.tick(entry, agent, AgentActivityKind.HUNTING,
                        "field-session", 1_001L).status());
        assertEquals(AgentActivityPhase.SUSPENDED, source.get().phase());
        assertEquals(AgentWorldDirectiveProcessor.Result.Status.PROGRESSED,
                processor.tick(entry, agent, null, "", 1_002L).status());
        assertEquals(AgentWorldDirectiveProcessor.Result.Status.PROGRESSED,
                processor.tick(entry, agent, null, "", 1_003L).status());
        assertEquals(AgentWorldDirectiveProcessor.Result.Status.COMPLETED,
                processor.tick(entry, agent, null, "", 1_004L).status());

        assertEquals(AgentWorldDirectiveStatus.COMPLETED,
                inbox.load(27, "start-quest").orElseThrow().status());
        assertTrue(handoffStore.list().isEmpty());
    }

    @Test
    void suspendsThroughBoundLifecycleAndRetainsExactSession() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions-lifecycle"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives-lifecycle"));
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L));
        AgentWorldDirective suspend = new AgentWorldDirective(
                1, "suspend-active", 27, AgentWorldDirectiveType.SUSPEND_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, null, null, "", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                10, 1_000L, 0L, "operator pause");
        inbox.submit(suspend, 1_000L);
        AtomicInteger attempts = new AtomicInteger();
        AgentWorldDirectiveProcessor processor = new AgentWorldDirectiveProcessor(
                sessions, inbox,
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory.resolve("handoffs-lifecycle"))),
                (directive, entry, agent, sourceKind, sourceSessionId) -> binding(),
                (session, directive, entry, agent, nowMs) ->
                        AgentWorldDirectorRolloutGateResult.allow("test gate"),
                (directive, session, entry, agent, kind, sessionId, nowMs) ->
                        attempts.incrementAndGet() == 1
                                ? AgentWorldActivityLifecycleHandler.Result.progressed(
                                "walking to safe spot", AgentActivityKind.HUNTING, "field-session")
                                : AgentWorldActivityLifecycleHandler.Result.completed(
                                "parked safely", AgentActivityKind.HUNTING, "field-session"),
                60_000L);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);

        assertEquals(AgentWorldDirectiveProcessor.Result.Status.PROGRESSED,
                processor.tick(mock(AgentRuntimeEntry.class), agent,
                        AgentActivityKind.HUNTING, "field-session", 1_001L).status());
        assertEquals(AgentWorldDirectorPhase.HANDOFF,
                sessions.load(27).orElseThrow().phase());
        assertEquals(AgentWorldDirectiveProcessor.Result.Status.COMPLETED,
                processor.tick(mock(AgentRuntimeEntry.class), agent,
                        null, "", 1_002L).status());
        AgentWorldDirectorSession retained = sessions.load(27).orElseThrow();
        assertEquals(AgentWorldDirectorPhase.PAUSED, retained.phase());
        assertEquals(AgentActivityKind.HUNTING, retained.observedActivityKind());
        assertEquals("field-session", retained.observedSessionId());
    }

    @Test
    void idleStartCompletesNormalTransferBeforeDestinationAdmission() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions-transfer"));
        AgentFileWorldDirectiveInbox inbox =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives-transfer"));
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L));
        inbox.submit(directive(), 1_000L);
        AtomicInteger transfers = new AtomicInteger();
        AtomicInteger admissions = new AtomicInteger();
        AgentWorldActivityBinding base = binding();
        AgentWorldActivityBinding traveling = new AgentWorldActivityBinding(
                base.source(), base.targetPreflight(), nowMs ->
                transfers.incrementAndGet() == 1
                        ? server.agents.runtime.activity.session.AgentActivityTransferPort.Result
                        .pending("walking through portals", nowMs + 500L)
                        : server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready(),
                nowMs -> {
                    admissions.incrementAndGet();
                    return base.target().requestEntry(nowMs);
                }, base.rollback(), base.outcome());
        AgentWorldDirectiveProcessor processor = new AgentWorldDirectiveProcessor(
                sessions, inbox,
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory.resolve("handoffs-transfer"))),
                (directive, entry, agent, sourceKind, sourceSessionId) -> traveling,
                (session, directive, entry, agent, nowMs) ->
                        AgentWorldDirectorRolloutGateResult.allow("test gate"),
                60_000L);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);

        assertEquals(AgentWorldDirectiveProcessor.Result.Status.PROGRESSED,
                processor.tick(mock(AgentRuntimeEntry.class), agent, null, "", 1_001L).status());
        assertEquals(AgentWorldDirectorPhase.STARTING,
                sessions.load(27).orElseThrow().phase());
        assertEquals(0, admissions.get());
        assertEquals(AgentWorldDirectiveProcessor.Result.Status.COMPLETED,
                processor.tick(mock(AgentRuntimeEntry.class), agent, null, "", 1_501L).status());
        assertEquals(1, admissions.get());
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

    private static AgentWorldActivityBinding handoffBinding(
            AtomicReference<AgentActivitySessionSnapshot> source) {
        AgentActivitySessionSnapshot active = new AgentActivitySessionSnapshot(
                AgentActivityKind.QUESTING, AgentActivityPhase.ACTIVE, "quest-session",
                "start-quest", "world-director", "27", 1_004L, "");
        return new AgentWorldActivityBinding(
                new server.agents.runtime.activity.session.AgentActivitySourcePort() {
                    @Override public AgentActivitySessionSnapshot snapshot(long nowMs) {
                        return source.get();
                    }
                    @Override public server.agents.runtime.activity.session.AgentActivityExitResult
                            requestGracefulExit(String reason, long nowMs, long deadlineMs) {
                        AgentActivitySessionSnapshot current = source.get();
                        source.set(new AgentActivitySessionSnapshot(current.kind(),
                                AgentActivityPhase.SUSPENDED, current.sessionId(),
                                current.requestId(), current.callerId(), current.agentId(),
                                nowMs, reason));
                        return server.agents.runtime.activity.session.AgentActivityExitResult.requested(
                                reason);
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
