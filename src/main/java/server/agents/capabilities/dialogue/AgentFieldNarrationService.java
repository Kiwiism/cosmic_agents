package server.agents.capabilities.dialogue;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventBus;
import server.agents.events.AgentEventListener;
import server.agents.events.AgentEventPriority;
import server.agents.field.AgentFieldObservationState;
import server.agents.field.events.AgentFieldAssignmentChangedEvent;
import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.field.events.AgentFieldPopulationChangedEvent;
import server.agents.field.events.AgentFieldRestEvent;
import server.agents.operations.events.AgentCombatPostureChangedEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Converts semantic field transitions into observer-gated, rate-limited chat intents. */
public final class AgentFieldNarrationService implements AgentEventListener<AgentEvent> {
    public static final String LIFECYCLE_INTENT = "field.lifecycle";
    public static final String ASSIGNMENT_INTENT = "field.assignment";
    public static final String POPULATION_INTENT = "field.population";
    public static final String REST_INTENT = "field.rest";
    public static final String POSTURE_INTENT = "field.posture";
    private static final long COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.dialogue.AgentFieldNarrationService.COOLDOWN_MS");
    private static final long MAP_WINDOW_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.dialogue.AgentFieldNarrationService.MAP_WINDOW_MS");
    private static final int MAP_MESSAGES_PER_WINDOW = config.AgentTuning.intValue(
            "server.agents.capabilities.dialogue.AgentFieldNarrationService.MAP_MESSAGES_PER_WINDOW");
    private static final Map<Integer, Window> mapWindows = new ConcurrentHashMap<>();

    private final AgentRuntimeEntry entry;
    private final AgentEventBus eventBus;

    public AgentFieldNarrationService(AgentRuntimeEntry entry, AgentEventBus eventBus) {
        this.entry = entry;
        this.eventBus = eventBus;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentFieldObservationState.NarrationLevel level = entry.capabilityStates()
                .require(AgentFieldObservationState.STATE_KEY).narrationLevel();
        if (level == AgentFieldObservationState.NarrationLevel.OFF) {
            return;
        }
        AgentDialogueIntentEvent intent = event instanceof AgentFieldLifecycleEvent lifecycle
                ? lifecycle(lifecycle)
                : event instanceof AgentFieldAssignmentChangedEvent assignment
                ? assignment(assignment, level)
                : event instanceof AgentFieldPopulationChangedEvent population
                ? population(population, level)
                : event instanceof AgentFieldRestEvent rest
                ? rest(rest)
                : event instanceof AgentCombatPostureChangedEvent posture
                ? posture(posture, level) : null;
        int mapId = mapId(event);
        if (intent != null && permit(mapId, intent.occurredAtMs())) {
            eventBus.publish(intent, AgentEventPriority.AMBIENT);
        }
    }

    private static AgentDialogueIntentEvent lifecycle(AgentFieldLifecycleEvent event) {
        Map<String, String> values = base(event.phase().name(), event.reason());
        values.put("sessionId", event.sessionId());
        values.put("objectiveId", event.objectiveId());
        return intent(event.agentId(), event.occurredAtMs(), LIFECYCLE_INTENT,
                event.dedupeKey(), values);
    }

    private static AgentDialogueIntentEvent assignment(
            AgentFieldAssignmentChangedEvent event,
            AgentFieldObservationState.NarrationLevel level) {
        Map<String, String> values = base("ASSIGNED", event.reason());
        values.put("role", event.role().name());
        values.put("partySlot", String.valueOf(event.partySlot() + 1));
        values.put("anchor", event.anchor().x + "," + event.anchor().y);
        if (level == AgentFieldObservationState.NarrationLevel.VERBOSE) {
            values.put("cells", event.cellIds().toString());
            values.put("regions", event.regionIds().toString());
        }
        return intent(event.agentId(), event.occurredAtMs(), ASSIGNMENT_INTENT,
                event.dedupeKey(), values);
    }

    private static AgentDialogueIntentEvent population(
            AgentFieldPopulationChangedEvent event,
            AgentFieldObservationState.NarrationLevel level) {
        if (level != AgentFieldObservationState.NarrationLevel.VERBOSE
                && event.change() == AgentFieldPopulationChangedEvent.Change.REBALANCED) {
            return null;
        }
        Map<String, String> values = base(event.change().name(), event.reason());
        values.put("population", String.valueOf(event.population()));
        return intent(event.agentId(), event.occurredAtMs(), POPULATION_INTENT,
                event.dedupeKey(), values);
    }

    private static AgentDialogueIntentEvent rest(AgentFieldRestEvent event) {
        Map<String, String> values = base(event.phase().name(), event.reason());
        values.put("target", event.target().x + "," + event.target().y);
        values.put("seconds", String.valueOf((event.plannedDurationMs() + 999L) / 1_000L));
        return intent(event.agentId(), event.occurredAtMs(), REST_INTENT,
                event.dedupeKey(), values);
    }

    private static AgentDialogueIntentEvent posture(
            AgentCombatPostureChangedEvent event,
            AgentFieldObservationState.NarrationLevel level) {
        if (level != AgentFieldObservationState.NarrationLevel.VERBOSE
                && event.posture() != AgentCombatPostureChangedEvent.Posture.SAFE_SHOT
                && event.posture() != AgentCombatPostureChangedEvent.Posture.KITING
                && event.posture() != AgentCombatPostureChangedEvent.Posture.JUMP_ATTACK
                && event.posture() != AgentCombatPostureChangedEvent.Posture.AOE_REPOSITION) {
            return null;
        }
        Map<String, String> values = base(event.posture().name(), event.reason());
        values.put("targetMobId", String.valueOf(event.targetMobId()));
        values.put("target", event.targetPosition().x + "," + event.targetPosition().y);
        return intent(event.agentId(), event.occurredAtMs(), POSTURE_INTENT,
                event.dedupeKey(), values);
    }

    private static Map<String, String> base(String phase, String reason) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("phase", phase == null ? "" : phase);
        values.put("reason", reason == null ? "" : reason);
        return values;
    }

    private static AgentDialogueIntentEvent intent(
            int agentId, long occurredAtMs, String key, String dedupe, Map<String, String> values) {
        return new AgentDialogueIntentEvent(agentId, occurredAtMs, key,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, dedupe, COOLDOWN_MS, values);
    }

    private static int mapId(AgentEvent event) {
        if (event instanceof AgentFieldLifecycleEvent value) return value.mapId();
        if (event instanceof AgentFieldAssignmentChangedEvent value) return value.mapId();
        if (event instanceof AgentFieldPopulationChangedEvent value) return value.mapId();
        if (event instanceof AgentFieldRestEvent value) return value.mapId();
        if (event instanceof AgentCombatPostureChangedEvent value) return value.mapId();
        return 0;
    }

    private static boolean permit(int mapId, long nowMs) {
        if (mapId <= 0) {
            return false;
        }
        Window window = mapWindows.computeIfAbsent(mapId, ignored -> new Window(nowMs));
        synchronized (window) {
            if (nowMs - window.startedAtMs >= MAP_WINDOW_MS) {
                window.startedAtMs = nowMs;
                window.messages = 0;
            }
            if (window.messages >= MAP_MESSAGES_PER_WINDOW) {
                return false;
            }
            window.messages++;
            return true;
        }
    }

    private static final class Window {
        private long startedAtMs;
        private int messages;

        private Window(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }
    }
}
