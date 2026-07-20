package server.agents.events.journal;

import org.junit.jupiter.api.Test;
import server.agents.events.AgentDomainEvent;
import server.agents.progression.events.AgentJobAdvancedEvent;
import server.agents.progression.events.AgentQuestStateChangedEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDurableEventPolicyTest {
    @Test
    void selectsMilestonesAndTerminalFailuresOnly() {
        assertTrue(AgentDurableEventPolicy.shouldJournal(
                new AgentJobAdvancedEvent(1, 10L, 0, 100, 10, 100000000, "job")));
        assertTrue(AgentDurableEventPolicy.shouldJournal(
                new AgentQuestStateChangedEvent(1, 10L, 1000, 1, 2, 1012000,
                        100000000, null, "quest")));
        assertFalse(AgentDurableEventPolicy.shouldJournal(
                new AgentQuestStateChangedEvent(1, 10L, 1000, 0, 1, 1012000,
                        100000000, null, "quest")));
        assertTrue(AgentDurableEventPolicy.shouldJournal(new AgentDomainEvent(
                1, 10L, "objective.failed", "objective:failed", Map.of())));
        assertTrue(AgentDurableEventPolicy.shouldJournal(new AgentDomainEvent(
                1, 10L, "lifecycle.transition", "active:failed",
                Map.of("to", "FAILED"))));
        assertFalse(AgentDurableEventPolicy.shouldJournal(new AgentDomainEvent(
                1, 10L, "navigation.route-selected", "route", Map.of())));
    }
}
