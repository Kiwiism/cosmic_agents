package server.agents.runtime.decision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDecisionReplayTest {
    @Test
    void identicalTraceProducesIdenticalFingerprint() {
        List<AgentDecisionRecord> trace = List.of(
                record(1, 100, "foreground", "plan"),
                record(2, 200, "combat-target", "mob:12"));

        AgentDecisionReplay.Result first = AgentDecisionReplay.verify(trace);
        AgentDecisionReplay.Result second = AgentDecisionReplay.verify(trace);

        assertTrue(first.valid());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals("mob:12", first.finalChoices().get("combat-target"));
    }

    @Test
    void rejectsOutOfOrderTrace() {
        AgentDecisionReplay.Result result = AgentDecisionReplay.verify(List.of(
                record(2, 100, "foreground", "plan"),
                record(1, 200, "foreground", "town-life")));

        assertFalse(result.valid());
    }

    private static AgentDecisionRecord record(
            long sequence, long nowMs, String domain, String choice) {
        return new AgentDecisionRecord(sequence, nowMs, domain, choice,
                "test", "v1", "fixture", "", List.of(choice));
    }
}
