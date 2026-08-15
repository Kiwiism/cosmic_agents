package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeActivityEvent;
import server.agents.capabilities.townlife.AgentTownLifeEncounterEvent;
import server.agents.capabilities.townlife.AgentTownLifeLifecycleEvent;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.townlife.AgentTownLifeTestObservationState;
import server.agents.runtime.townlife.AgentTownLifeTestScenarioEvent;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts structured TownLife test phases into rate-limited observer narration. */
public final class AgentTownLifeTestNarrationService implements AgentEventListener<AgentEvent> {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.dialogue.AgentTownLifeTestNarrationService.";
    public static final String ACTIVITY_INTENT = "townlife.test.activity";
    public static final String LIFECYCLE_INTENT = "townlife.test.lifecycle";
    public static final String ENCOUNTER_INTENT = "townlife.test.encounter";
    public static final String SCENARIO_INTENT = "townlife.test.scenario";
    private static final long COOLDOWN_MS = tuningLong("COOLDOWN_MS");

    private final AgentRuntimeEntry entry;
    private final AgentEventBus eventBus;

    public AgentTownLifeTestNarrationService(AgentRuntimeEntry entry, AgentEventBus eventBus) {
        this.entry = entry;
        this.eventBus = eventBus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentTownLifeTestObservationState observation = entry.capabilityStates()
                .require(AgentTownLifeTestObservationState.STATE_KEY);
        if (!observation.enabled()) {
            return;
        }
        AgentDialogueIntentEvent intent = event instanceof AgentTownLifeActivityEvent activity
                ? activityIntent(activity, observation.scenarioId())
                : event instanceof AgentTownLifeLifecycleEvent lifecycle
                ? lifecycleIntent(lifecycle, observation.scenarioId())
                : event instanceof AgentTownLifeEncounterEvent encounter
                ? encounterIntent(encounter, observation.scenarioId())
                : event instanceof AgentTownLifeTestScenarioEvent scenario
                ? scenarioIntent(scenario) : null;
        if (intent != null) {
            observation.recordAnnouncement();
            eventBus.publish(intent, AgentEventPriority.AMBIENT);
            if (event instanceof AgentTownLifeLifecycleEvent lifecycle
                    && observation.autoDisableOnExit()
                    && (lifecycle.phase() == AgentTownLifeLifecycleEvent.Phase.EXITED
                    || lifecycle.phase() == AgentTownLifeLifecycleEvent.Phase.FORCED
                    || lifecycle.phase() == AgentTownLifeLifecycleEvent.Phase.TIMED_OUT)) {
                observation.disable();
            }
            if (event instanceof AgentTownLifeTestScenarioEvent scenario
                    && (scenario.phase() == AgentTownLifeTestScenarioEvent.Phase.COMPLETED
                    || scenario.phase() == AgentTownLifeTestScenarioEvent.Phase.FAILED)) {
                observation.disable();
            }
        }
    }

    private AgentDialogueIntentEvent activityIntent(
            AgentTownLifeActivityEvent event, String scenarioId) {
        if (event.phase() != AgentTownLifeActivityEvent.Phase.SELECTED
                && event.phase() != AgentTownLifeActivityEvent.Phase.ORIENTING
                && event.phase() != AgentTownLifeActivityEvent.Phase.COMPLETED
                && event.phase() != AgentTownLifeActivityEvent.Phase.ABANDONED
                && event.phase() != AgentTownLifeActivityEvent.Phase.TIMED_OUT) {
            return null;
        }
        AgentTownLifeState state = entry.capabilityStates()
                .require(AgentTownLifeState.STATE_KEY);
        Map<String, String> parameters = base(scenarioId);
        parameters.put("phase", event.phase().name());
        parameters.put("activity", event.activity().name());
        parameters.put("venue", venueLabel(event.mapId(), event.venueId()));
        Point target = state.target();
        if (target != null) {
            parameters.put("target", target.x + "," + target.y);
        }
        if (event.phase() == AgentTownLifeActivityEvent.Phase.ORIENTING) {
            parameters.put("remainingSeconds", String.valueOf(
                    Math.max(1L, (state.nextActionAtMs() - event.occurredAtMs() + 999L) / 1_000L)));
        }
        return intent(event.agentId(), event.occurredAtMs(), ACTIVITY_INTENT,
                scenarioId + ':' + event.correlationId() + ':' + event.phase(), parameters);
    }

    private AgentDialogueIntentEvent lifecycleIntent(
            AgentTownLifeLifecycleEvent event, String scenarioId) {
        if (event.phase() != AgentTownLifeLifecycleEvent.Phase.STARTED
                && event.phase() != AgentTownLifeLifecycleEvent.Phase.EXIT_REQUESTED
                && event.phase() != AgentTownLifeLifecycleEvent.Phase.EXITED
                && event.phase() != AgentTownLifeLifecycleEvent.Phase.FORCED
                && event.phase() != AgentTownLifeLifecycleEvent.Phase.TIMED_OUT) {
            return null;
        }
        Map<String, String> parameters = base(scenarioId);
        parameters.put("phase", event.phase().name());
        parameters.put("reason", event.reason());
        parameters.put("activity", event.finalActivity().name());
        return intent(event.agentId(), event.occurredAtMs(), LIFECYCLE_INTENT,
                scenarioId + ':' + event.sessionId() + ':' + event.phase(), parameters);
    }

    private AgentDialogueIntentEvent encounterIntent(
            AgentTownLifeEncounterEvent event, String scenarioId) {
        if (event.phase() != server.agents.capabilities.townlife.AgentTownLifeEncounterState.Phase.ACTIVE
                && event.phase() != server.agents.capabilities.townlife.AgentTownLifeEncounterState.Phase.REACTING
                && event.phase() != server.agents.capabilities.townlife.AgentTownLifeEncounterState.Phase.CLOSING) {
            return null;
        }
        Map<String, String> parameters = base(scenarioId);
        parameters.put("phase", event.phase().name());
        parameters.put("encounterType", event.encounterType().name());
        parameters.put("role", event.participantRole().name());
        parameters.put("venue", venueLabel(event.mapId(), event.venueId()));
        AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(event.peerAgentId());
        Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
        parameters.put("peerName", peer == null ? "friend" : peer.getName());
        return intent(event.agentId(), event.occurredAtMs(), ENCOUNTER_INTENT,
                scenarioId + ':' + event.encounterId() + ':' + event.agentId() + ':' + event.phase(),
                parameters);
    }

    private AgentDialogueIntentEvent scenarioIntent(AgentTownLifeTestScenarioEvent event) {
        Map<String, String> parameters = base(event.scenarioId());
        parameters.put("phase", event.phase().name());
        parameters.put("cycle", String.valueOf(event.cycle()));
        parameters.put("detail", event.detail());
        return intent(event.agentId(), event.occurredAtMs(), SCENARIO_INTENT,
                event.dedupeKey(), parameters);
    }

    private static Map<String, String> base(String scenarioId) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("scenarioId", scenarioId == null ? "" : scenarioId);
        return parameters;
    }

    private static AgentDialogueIntentEvent intent(
            int agentId, long occurredAtMs, String key, String dedupe,
            Map<String, String> parameters) {
        return new AgentDialogueIntentEvent(agentId, occurredAtMs, key,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, dedupe, COOLDOWN_MS, parameters);
    }

    private static String venueLabel(int mapId, String venueId) {
        if (venueId == null || venueId.isBlank()) {
            return "a nearby spot";
        }
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .find(mapId).orElse(null);
        return profile == null ? venueId
                : profile.venue(venueId).map(AgentTownLifeProfile.Venue::label).orElse(venueId);
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }
}
