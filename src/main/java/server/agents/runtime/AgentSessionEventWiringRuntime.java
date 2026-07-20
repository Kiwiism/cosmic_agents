package server.agents.runtime;

import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.capabilities.dialogue.AgentDialogueProjectionRuntime;
import server.agents.capabilities.dialogue.AgentDialogueProjectionService;
import server.agents.capabilities.dialogue.llm.context.AgentLlmContextProjectionService;
import server.agents.capabilities.supplies.AgentSupplyCoordinationProjectionService;
import server.agents.capabilities.supplies.AgentSupplyDialogueReactionService;
import server.agents.capabilities.supplies.AgentSupplyMaintenanceEventListener;
import server.agents.capabilities.supplies.AgentSupplyMonitoringProjectionService;
import server.agents.capabilities.supplies.AgentSupplyThresholdChangedEvent;
import server.agents.events.AgentEventSubscription;
import server.agents.events.BoundedAgentEventBus;
import server.agents.events.journal.AgentDurableEventJournalListener;
import server.agents.progression.events.AgentProgressionCheckpointProjectionService;
import server.agents.progression.events.AgentProgressionDialogueReactionService;
import server.agents.progression.events.AgentProgressionMonitoringProjectionService;
import server.agents.resources.events.AgentInventoryMaintenanceEventListener;
import server.agents.resources.events.AgentResourceDialogueReactionService;
import server.agents.resources.events.AgentResourceMonitoringProjectionService;
import server.agents.operations.events.AgentOperationalDialogueReactionService;
import server.agents.operations.events.AgentOperationalEvaluationListener;
import server.agents.operations.events.AgentOperationalMonitoringProjectionService;

import java.util.ArrayList;
import java.util.List;

/** Registers production listeners once and binds their lifetime to the Agent session. */
public final class AgentSessionEventWiringRuntime {
    private AgentSessionEventWiringRuntime() {
    }

    static void ensureWired(AgentRuntimeEntry entry, BoundedAgentEventBus bus) {
        AgentSessionEventWiringState state = entry.capabilityStates()
                .require(AgentSessionEventWiringState.STATE_KEY);
        synchronized (state) {
            if (state.wired()) {
                return;
            }
            List<AgentEventSubscription> subscriptions = new ArrayList<>();
            AgentEventRolloutConfig rollout = AgentEventRolloutConfig.fromSystemProperties();
            try {
                if (rollout.reactionsEnabled()) {
                    subscriptions.add(bus.subscribe(AgentSupplyThresholdChangedEvent.TYPE,
                            new AgentSupplyMaintenanceEventListener(entry)));
                }
                if (rollout.coordinationEnabled()) {
                    subscriptions.add(bus.subscribe(AgentSupplyThresholdChangedEvent.TYPE,
                            new AgentSupplyCoordinationProjectionService()));
                }
                if (rollout.dialogueEnabled()) {
                    subscriptions.add(bus.subscribe(AgentSupplyThresholdChangedEvent.TYPE,
                            new AgentSupplyDialogueReactionService(bus)));
                }
                subscriptions.add(bus.subscribe(AgentSupplyThresholdChangedEvent.TYPE,
                        new AgentSupplyMonitoringProjectionService(entry)));
                if (rollout.dialogueEnabled()) {
                    subscriptions.add(bus.subscribe(AgentDialogueIntentEvent.TYPE,
                            new AgentDialogueProjectionService(
                                    (agentId, audience) -> AgentDialogueProjectionRuntime.hasAudience(
                                            entry, agentId, audience),
                                    intent -> AgentDialogueProjectionRuntime.project(entry, intent))));
                }
                AgentProgressionMonitoringProjectionService progressionMonitoring =
                        new AgentProgressionMonitoringProjectionService(entry);
                AgentProgressionDialogueReactionService progressionDialogue =
                        new AgentProgressionDialogueReactionService(bus);
                AgentProgressionCheckpointProjectionService progressionCheckpoint =
                        new AgentProgressionCheckpointProjectionService(entry);
                subscriptions.add(bus.subscribe("*", progressionMonitoring));
                if (rollout.dialogueEnabled()) {
                    subscriptions.add(bus.subscribe("*", progressionDialogue));
                }
                if (rollout.reactionsEnabled()) {
                    subscriptions.add(bus.subscribe("*", progressionCheckpoint));
                }
                subscriptions.add(bus.subscribe("*",
                        new AgentResourceMonitoringProjectionService(entry)));
                if (rollout.reactionsEnabled()) {
                    subscriptions.add(bus.subscribe("*",
                            new AgentInventoryMaintenanceEventListener(entry)));
                }
                if (rollout.dialogueEnabled()) {
                    subscriptions.add(bus.subscribe("*",
                            new AgentResourceDialogueReactionService(bus)));
                }
                subscriptions.add(bus.subscribe("*",
                        new AgentOperationalMonitoringProjectionService(entry)));
                if (rollout.reactionsEnabled()) {
                    subscriptions.add(bus.subscribe("*",
                            new AgentOperationalEvaluationListener(entry)));
                }
                if (rollout.dialogueEnabled()) {
                    subscriptions.add(bus.subscribe("*",
                            new AgentOperationalDialogueReactionService(bus)));
                }
                subscriptions.add(bus.subscribe("*", new AgentDurableEventJournalListener()));
                if (rollout.llmContextEnabled()) {
                    subscriptions.add(bus.subscribe("*", new AgentLlmContextProjectionService(entry)));
                }
                state.attach(subscriptions);
            } catch (RuntimeException failure) {
                subscriptions.forEach(AgentEventSubscription::close);
                throw failure;
            }
        }
    }

    static void close(AgentRuntimeEntry entry) {
        entry.capabilityStates().remove(AgentSessionEventWiringState.STATE_KEY)
                .ifPresent(AgentSessionEventWiringState::close);
    }
}
