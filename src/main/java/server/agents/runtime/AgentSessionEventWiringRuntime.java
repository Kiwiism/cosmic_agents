package server.agents.runtime;

import config.YamlConfig;
import server.agents.capabilities.dialogue.AgentDialogueIntentEvent;
import server.agents.capabilities.dialogue.AgentDialogueProjectionRuntime;
import server.agents.capabilities.dialogue.AgentDialogueProjectionService;
import server.agents.capabilities.dialogue.AgentFieldNarrationService;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReactionService;
import server.agents.capabilities.dialogue.AgentTownLifeDialogueReactionService;
import server.agents.capabilities.dialogue.AgentTownLifeTestNarrationService;
import server.agents.capabilities.townlife.AgentTownLifeEncounterEvent;
import server.agents.capabilities.townlife.AgentTownLifeArrivalEvent;
import server.agents.capabilities.townlife.AgentTownLifeActivityEvent;
import server.agents.capabilities.townlife.AgentTownLifeLifecycleEvent;
import server.agents.runtime.townlife.AgentTownLifeTestScenarioEvent;
import server.agents.social.projection.AgentSocialContextProjectionService;
import server.agents.capabilities.supplies.AgentSupplyCoordinationProjectionService;
import server.agents.capabilities.supplies.AgentSupplyMaintenanceEventListener;
import server.agents.capabilities.supplies.AgentSupplyMonitoringProjectionService;
import server.agents.capabilities.supplies.AgentSupplyThresholdChangedEvent;
import server.agents.capabilities.presentation.AgentPersonalityPresentationEventListener;
import server.agents.capabilities.presentation.AgentPresentationProfile;
import server.agents.behavior.AgentBehaviorFeatureProfile;
import server.agents.events.AgentEventSubscription;
import server.agents.events.BoundedAgentEventBus;
import server.agents.events.journal.AgentDurableEventJournalListener;
import server.agents.progression.events.AgentProgressionCheckpointProjectionService;
import server.agents.progression.events.AgentProgressionDialogueReactionService;
import server.agents.progression.events.AgentProgressionMonitoringProjectionService;
import server.agents.progression.events.AgentQuestProgressDialogueReactionService;
import server.agents.progression.events.AgentQuestProgressMilestoneEvent;
import server.agents.resources.events.AgentInventoryMaintenanceEventListener;
import server.agents.resources.events.AgentResourceDialogueReactionService;
import server.agents.resources.events.AgentResourceMonitoringProjectionService;
import server.agents.operations.events.AgentOperationalDialogueReactionService;
import server.agents.operations.events.AgentOperationalEvaluationListener;
import server.agents.operations.events.AgentOperationalMonitoringProjectionService;
import server.agents.capabilities.behavior.AgentBehaviorEventListener;
import server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener;
import server.agents.capabilities.combat.AgentCombatTacticalEventListener;
import server.agents.capabilities.looting.AgentPostKillLootEventListener;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.progression.AgentHuntRecoveryEventListener;
import server.agents.journey.AgentJourneyEventListener;
import server.agents.field.AgentFieldEventListener;
import server.agents.field.AgentFieldObservationProjectionService;

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
            boolean questProgressDialogueEnabled =
                    AgentEventRolloutConfig.dialogueTransportEnabled()
                            && (config.AgentYamlConfig.config.agent.AGENT_AMHERST_INTENTION_CHAT_ENABLED
                            || config.AgentYamlConfig.config.agent.AGENT_VICTORIA_INTENTION_CHAT_ENABLED);
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
                subscriptions.add(bus.subscribe(AgentDialogueIntentEvent.TYPE,
                        new AgentDialogueProjectionService(
                                (agentId, audience) -> AgentDialogueProjectionRuntime.hasAudience(
                                        entry, agentId, audience),
                                intent -> AgentDialogueProjectionRuntime.project(entry, intent))));
                subscriptions.add(bus.subscribe("*",
                        new AgentFieldObservationProjectionService(entry)));
                subscriptions.add(bus.subscribe("*", new AgentFieldNarrationService(entry, bus)));
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
                AgentTownLifeTestNarrationService townLifeTestNarration =
                        new AgentTownLifeTestNarrationService(entry, bus);
                subscriptions.add(bus.subscribe(AgentTownLifeActivityEvent.TYPE,
                        townLifeTestNarration));
                subscriptions.add(bus.subscribe(AgentTownLifeLifecycleEvent.TYPE,
                        townLifeTestNarration));
                subscriptions.add(bus.subscribe(AgentTownLifeEncounterEvent.TYPE,
                        townLifeTestNarration));
                subscriptions.add(bus.subscribe(AgentTownLifeTestScenarioEvent.TYPE,
                        townLifeTestNarration));
                if (questProgressDialogueEnabled) {
                    subscriptions.add(bus.subscribe(AgentQuestProgressMilestoneEvent.TYPE,
                            new AgentQuestProgressDialogueReactionService(bus)));
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
                    AgentTownLifeDialogueReactionService townLifeDialogue =
                            new AgentTownLifeDialogueReactionService(bus);
                    subscriptions.add(bus.subscribe(AgentTownLifeEncounterEvent.TYPE,
                            townLifeDialogue));
                    subscriptions.add(bus.subscribe(AgentTownLifeArrivalEvent.TYPE,
                            townLifeDialogue));
                }
                if (AgentPresentationProfile.current().enabled()) {
                    subscriptions.add(bus.subscribe("*",
                            new AgentPersonalityPresentationEventListener(entry)));
                    subscriptions.add(bus.subscribe(AgentQuestStateChangedEvent.TYPE,
                            new AgentPioRelaxerInterludeEventListener(entry)));
                }
                if (AgentBehaviorFeatureProfile.current().enabled()) {
                    subscriptions.add(bus.subscribe("*", new AgentBehaviorEventListener(entry)));
                }
                AgentCombatTacticalEventListener combatTactical =
                        new AgentCombatTacticalEventListener(entry);
                subscriptions.add(bus.subscribe(
                        server.agents.operations.events.AgentMobKilledEvent.TYPE,
                        combatTactical));
                subscriptions.add(bus.subscribe(
                        server.agents.operations.events.AgentMobDamagedEvent.TYPE,
                        combatTactical));
                subscriptions.add(bus.subscribe(
                        server.agents.operations.events.AgentMobDamagedEvent.TYPE,
                        new AgentHuntRecoveryEventListener(entry)));
                subscriptions.add(bus.subscribe(
                        server.agents.operations.events.AgentMobKilledEvent.TYPE,
                        new AgentPostKillLootEventListener(entry)));
                subscriptions.add(bus.subscribe(
                        server.agents.operations.events.AgentMobKilledEvent.TYPE,
                        new AgentFieldEventListener(entry)));
                subscriptions.add(bus.subscribe("*", new AgentDurableEventJournalListener()));
                subscriptions.add(bus.subscribe("*",
                        new server.agents.economy.activity.ActivityCalibrationEventListener()));
                subscriptions.add(bus.subscribe("*", new AgentJourneyEventListener()));
                if (rollout.llmContextEnabled()) {
                    subscriptions.add(bus.subscribe("*", new AgentSocialContextProjectionService(entry)));
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
