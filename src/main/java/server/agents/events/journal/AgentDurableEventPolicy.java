package server.agents.events.journal;

import server.agents.events.AgentDomainEvent;
import server.agents.events.AgentEvent;
import server.agents.operations.events.AgentLifeStateChangedEvent;
import server.agents.progression.events.AgentJobAdvancedEvent;
import server.agents.progression.events.AgentLevelChangedEvent;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.resources.events.AgentScrollResolvedEvent;
import server.agents.resources.events.AgentShopTransactionEvent;

import java.util.Set;

/** Keeps the disk journal focused on durable milestones and exceptional safety state. */
public final class AgentDurableEventPolicy {
    private static final Set<String> TERMINAL_OBJECTIVE_TYPES = Set.of(
            "objective.succeeded", "objective.blocked", "objective.cancelled",
            "objective.failed", "objective.superseded");

    private AgentDurableEventPolicy() {
    }

    public static boolean shouldJournal(AgentEvent event) {
        if (event instanceof AgentJobAdvancedEvent || event instanceof AgentLevelChangedEvent
                || event instanceof AgentShopTransactionEvent || event instanceof AgentScrollResolvedEvent) {
            return true;
        }
        if (event instanceof AgentQuestStateChangedEvent quest) {
            return quest.status() == 2;
        }
        if (event instanceof AgentLifeStateChangedEvent life) {
            return "DEAD".equalsIgnoreCase(life.state());
        }
        if (!(event instanceof AgentDomainEvent domain)) {
            return false;
        }
        if (TERMINAL_OBJECTIVE_TYPES.contains(domain.type())) {
            return true;
        }
        return "lifecycle.transition".equals(domain.type())
                && ("FAILED".equals(domain.attributes().get("to"))
                || "QUARANTINED".equals(domain.attributes().get("to")));
    }
}
