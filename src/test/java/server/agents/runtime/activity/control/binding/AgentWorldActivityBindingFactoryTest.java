package server.agents.runtime.activity.control.binding;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorldActivityBindingFactoryTest {
    @Test
    void dispatchesToRegisteredSystemProviderWithoutKindSwitching() {
        AgentWorldActivityBinding expected = binding();
        AgentWorldActivityBindingFactory factory = new AgentWorldActivityBindingFactory(List.of(
                provider(AgentActivityKind.QUESTING, expected)));

        assertSame(expected, factory.bind(request(AgentActivityKind.QUESTING)));
        assertThrows(IllegalStateException.class,
                () -> factory.bind(request(AgentActivityKind.HUNTING)));
    }

    @Test
    void rejectsDuplicateProviderOwnership() {
        assertThrows(IllegalArgumentException.class, () ->
                new AgentWorldActivityBindingFactory(List.of(
                        provider(AgentActivityKind.QUESTING, binding()),
                        provider(AgentActivityKind.QUESTING, binding()))));
    }

    private static AgentWorldActivityBindingRequest request(AgentActivityKind target) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        AgentWorldDirective directive = new AgentWorldDirective(
                1, "directive", 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, target,
                AgentWorldActivityRequestType.INDIVIDUAL_QUEST, "request", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 1, 1_000L, 0L, "test");
        return new AgentWorldActivityBindingRequest(
                directive, mock(AgentRuntimeEntry.class), agent, null, "", Map.of());
    }

    private static AgentWorldActivityBindingProvider provider(
            AgentActivityKind kind, AgentWorldActivityBinding binding) {
        return new AgentWorldActivityBindingProvider() {
            @Override public AgentActivityKind targetKind() { return kind; }
            @Override public AgentWorldActivityBinding bind(
                    AgentWorldActivityBindingRequest request) { return binding; }
        };
    }

    private static AgentWorldActivityBinding binding() {
        return new AgentWorldActivityBinding(
                new server.agents.runtime.activity.session.AgentActivitySourcePort() {
                    @Override public AgentActivitySessionSnapshot snapshot(long nowMs) {
                        return AgentActivitySessionSnapshot.idle(AgentActivityKind.TOWN_LIFE, "27");
                    }
                    @Override public server.agents.runtime.activity.session.AgentActivityExitResult
                            requestGracefulExit(String reason, long nowMs, long deadlineMs) {
                        return server.agents.runtime.activity.session.AgentActivityExitResult.released(reason);
                    }
                },
                (agentId, kind, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityPreflightPort.Result.allowed(),
                nowMs -> server.agents.runtime.activity.session.AgentActivityTransferPort.Result.ready(),
                nowMs -> AgentActivityAdmissionResult.accepted(
                        new AgentActivitySessionSnapshot(AgentActivityKind.QUESTING,
                                server.agents.runtime.activity.session.AgentActivityPhase.ACTIVE,
                                "quest-1", "request", "director", "27", 1_000L, "")),
                (sessionId, nowMs) ->
                        server.agents.runtime.activity.session.AgentActivityRollbackPort.Result.resumed("ok"),
                nowMs -> null);
    }
}
