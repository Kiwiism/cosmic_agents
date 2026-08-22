package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectiveTest {
    @Test
    void activityDirectiveCarriesEnoughIdentityForDurableReplay() {
        AgentWorldDirective directive = new AgentWorldDirective(1, "directive-27-1", 27,
                AgentWorldDirectiveType.START_ACTIVITY, AgentWorldDirectiveSource.OPERATOR,
                null, AgentActivityKind.QUESTING, AgentWorldActivityRequestType.INDIVIDUAL_QUEST,
                "quest:1001", Map.of("questId", "1001"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 100, 1_000L, 2_000L,
                "operator selected quest");

        assertFalse(directive.expiredAt(1_999L));
        assertTrue(directive.expiredAt(2_000L));
    }

    @Test
    void rejectsIncompleteActivityAndModeRequests() {
        assertThrows(IllegalArgumentException.class, () -> directive(
                AgentWorldDirectiveType.START_ACTIVITY, null, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> directive(
                AgentWorldDirectiveType.SET_MODE, null, null, null, ""));
    }

    private static AgentWorldDirective directive(
            AgentWorldDirectiveType type,
            AgentWorldDirectorMode mode,
            AgentActivityKind kind,
            AgentWorldActivityRequestType requestType,
            String requestId) {
        return new AgentWorldDirective(1, "directive-1", 1, type,
                AgentWorldDirectiveSource.OPERATOR, mode, kind, requestType, requestId, Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 0, 1L, 0L, "test");
    }
}
