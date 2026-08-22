package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentFileWorldDirectiveInboxTest {
    @TempDir
    Path directory;

    @Test
    void persistsPriorityClaimAndTerminalAcknowledgementAcrossRestart() {
        AgentFileWorldDirectiveInbox inbox = new AgentFileWorldDirectiveInbox(directory);
        inbox.submit(directive("low", 1, 2_000L), 1_000L);
        inbox.submit(directive("high", 10, 2_000L), 1_000L);

        AgentWorldDirectiveEnvelope selected = inbox.nextPending(27, 1_100L).orElseThrow();
        assertEquals("high", selected.directive().directiveId());
        inbox.claim(27, "high", 1_101L);
        inbox.resolve(27, "high", AgentWorldDirectiveStatus.COMPLETED, "done", 1_200L);

        AgentFileWorldDirectiveInbox restarted = new AgentFileWorldDirectiveInbox(directory);
        assertEquals(AgentWorldDirectiveStatus.COMPLETED,
                restarted.load(27, "high").orElseThrow().status());
        assertEquals("low", restarted.nextPending(27, 1_201L).orElseThrow()
                .directive().directiveId());
    }

    @Test
    void duplicateSubmissionIsIdempotentButConflictingContentIsRejected() {
        AgentFileWorldDirectiveInbox inbox = new AgentFileWorldDirectiveInbox(directory);
        AgentWorldDirective original = directive("same", 1, 0L);
        assertEquals(inbox.submit(original, 1_000L), inbox.submit(original, 1_001L));

        assertThrows(IllegalStateException.class,
                () -> inbox.submit(directive("same", 2, 0L), 1_002L));
    }

    @Test
    void expiresPendingDirectiveBeforeSelection() {
        AgentFileWorldDirectiveInbox inbox = new AgentFileWorldDirectiveInbox(directory);
        inbox.submit(directive("expired", 1, 1_100L), 1_000L);

        assertEquals(0, inbox.nextPending(27, 1_100L).stream().count());
        assertEquals(AgentWorldDirectiveStatus.EXPIRED,
                inbox.load(27, "expired").orElseThrow().status());
    }

    private static AgentWorldDirective directive(String id, int priority, long expiresAtMs) {
        return new AgentWorldDirective(1, id, 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, AgentActivityKind.QUESTING,
                AgentWorldActivityRequestType.INDIVIDUAL_QUEST, "quest:1001", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, priority, 1_000L,
                expiresAtMs, "test");
    }
}
