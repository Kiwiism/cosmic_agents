package server.agents.capabilities.dialogue.llm.context;

import server.agents.capabilities.supplies.AgentSupplyThresholdChangedEvent;
import server.agents.events.AgentContextualEvent;
import server.agents.events.AgentDomainEvent;
import server.agents.events.AgentEvent;
import server.agents.operations.events.AgentLifeStateChangedEvent;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.operations.events.AgentNavigationRouteFailedEvent;
import server.agents.operations.events.AgentRecoveryPerformedEvent;
import server.agents.progression.events.AgentJobAdvancedEvent;
import server.agents.progression.events.AgentLevelChangedEvent;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.progression.events.AgentSkillLearnedEvent;
import server.agents.resources.events.AgentEquipmentLoadoutChangedEvent;
import server.agents.resources.events.AgentInventoryThresholdChangedEvent;
import server.agents.resources.events.AgentScrollResolvedEvent;
import server.agents.resources.events.AgentShopTransactionEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded structured context assembled from authoritative Agent facts. */
public final class AgentLlmContextProjectionState {
    static final int MAX_FACTS = 48;
    static final int MAX_MILESTONES = 24;
    public static final AgentCapabilityStateKey<AgentLlmContextProjectionState> STATE_KEY =
            new AgentCapabilityStateKey<>("dialogue.llm-event-context",
                    AgentLlmContextProjectionState.class, AgentLlmContextProjectionState::new);

    private final LinkedHashMap<String, String> facts = new LinkedHashMap<>();
    private final ArrayDeque<AgentLlmContextFact> milestones = new ArrayDeque<>();
    private int agentId;
    private long revision;

    public synchronized void record(AgentEvent event) {
        if (event == null) {
            return;
        }
        agentId = event.agentId();
        boolean recognized = project(event);
        if (recognized) {
            revision++;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(agentId, revision, Map.copyOf(facts), List.copyOf(milestones));
    }

    private boolean project(AgentEvent event) {
        if (event instanceof AgentLevelChangedEvent level) {
            put("progression.level", String.valueOf(level.level()));
            put("progression.jobId", String.valueOf(level.jobId()));
            context(level);
            milestone(level, "level=" + level.level());
        } else if (event instanceof AgentJobAdvancedEvent job) {
            put("progression.jobId", String.valueOf(job.jobId()));
            put("progression.level", String.valueOf(job.level()));
            context(job);
            milestone(job, "jobId=" + job.jobId());
        } else if (event instanceof AgentQuestStateChangedEvent quest) {
            context(quest);
            if (quest.status() == 2) {
                milestone(quest, "questId=" + quest.questId() + ",status=completed");
            }
        } else if (event instanceof AgentSkillLearnedEvent skill) {
            context(skill);
            milestone(skill, "skillId=" + skill.skillId() + ",level=" + skill.skillLevel());
        } else if (event instanceof AgentSupplyThresholdChangedEvent supply) {
            put("supply." + supply.category().name().toLowerCase(),
                    supply.currentQuantity() + "/" + supply.targetQuantity()
                            + ",urgency=" + supply.urgency());
            context(supply);
        } else if (event instanceof AgentInventoryThresholdChangedEvent inventory) {
            put("inventory." + inventory.inventoryType().toLowerCase(),
                    "free=" + inventory.freeSlots() + "/" + inventory.slotLimit()
                            + ",threshold=" + inventory.threshold());
            context(inventory);
        } else if (event instanceof AgentEquipmentLoadoutChangedEvent loadout) {
            put("equipment.lastChange", loadout.reason());
            context(loadout);
            milestone(loadout, "slots=" + loadout.loadout().size() + ",reason=" + loadout.reason());
        } else if (event instanceof AgentShopTransactionEvent shop) {
            put("shopping.lastTransaction", shop.operation() + ":item=" + shop.itemId()
                    + ",quantity=" + shop.quantity() + ",result=" + shop.result());
            context(shop);
            milestone(shop, facts.get("shopping.lastTransaction"));
        } else if (event instanceof AgentScrollResolvedEvent scroll) {
            put("equipment.lastScroll", "item=" + scroll.scrollItemId() + ",result=" + scroll.result());
            context(scroll);
            milestone(scroll, facts.get("equipment.lastScroll"));
        } else if (event instanceof AgentMapTransitionedEvent map) {
            put("world.mapId", String.valueOf(map.mapId()));
            context(map);
            milestone(map, "from=" + map.previousMapId() + ",to=" + map.mapId());
        } else if (event instanceof AgentNavigationRouteFailedEvent failure) {
            put("navigation.blocker", failure.reason());
            context(failure);
            milestone(failure, "route=" + failure.startRegionId() + "->"
                    + failure.targetRegionId() + ",reason=" + failure.reason());
        } else if (event instanceof AgentRecoveryPerformedEvent recovery) {
            put("recovery.last", recovery.recoveryType());
            put("navigation.blocker", "");
            context(recovery);
            milestone(recovery, recovery.recoveryType());
        } else if (event instanceof AgentLifeStateChangedEvent life) {
            put("combat.lifeState", life.state());
            context(life);
            milestone(life, "state=" + life.state());
        } else if (event instanceof AgentDomainEvent domain && domain.type().startsWith("objective.")) {
            projectObjective(domain);
        } else {
            return false;
        }
        return true;
    }

    private void projectObjective(AgentDomainEvent event) {
        String objectiveId = event.attributes().getOrDefault("objectiveId", "");
        String status = event.type().substring("objective.".length());
        if ("active".equals(status) || "resumed".equals(status)) {
            put("objective.active", objectiveId);
            put("objective.blocker", "");
        } else {
            if (objectiveId.equals(facts.get("objective.active"))) {
                put("objective.active", "");
            }
            if ("blocked".equals(status) || "failed".equals(status)) {
                put("objective.blocker", event.attributes().getOrDefault("reason", status));
            }
        }
        milestone(event, "objectiveId=" + objectiveId + ",status=" + status);
    }

    private void context(AgentContextualEvent event) {
        if (event.mapId() >= 0) {
            put("world.mapId", String.valueOf(event.mapId()));
        }
        if (!event.objectiveId().isBlank()) {
            put("objective.active", event.objectiveId());
        }
    }

    private void milestone(AgentEvent event, String summary) {
        while (milestones.size() >= MAX_MILESTONES) {
            milestones.removeFirst();
        }
        milestones.addLast(new AgentLlmContextFact(event.occurredAtMs(), event.type(), summary,
                event.context().objectiveId(), event.context().mapId()));
    }

    private void put(String key, String value) {
        if (!facts.containsKey(key) && facts.size() >= MAX_FACTS) {
            String oldest = facts.keySet().iterator().next();
            facts.remove(oldest);
        }
        facts.put(key, value == null ? "" : value);
    }

    public record Snapshot(
            int agentId,
            long revision,
            Map<String, String> facts,
            List<AgentLlmContextFact> milestones) {
    }
}
