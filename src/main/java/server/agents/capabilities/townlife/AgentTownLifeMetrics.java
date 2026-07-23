package server.agents.capabilities.townlife;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Process-local, allocation-light TownLife safety counters. These are observability
 * facts only and never influence policy.
 */
public final class AgentTownLifeMetrics {
    private static final Map<AgentTownLifeActivityEvent.Phase, LongAdder> ACTIVITY_PHASES =
            counters(AgentTownLifeActivityEvent.Phase.class);
    private static final Map<AgentTownLifeEncounterState.Phase, LongAdder> ENCOUNTER_PHASES =
            counters(AgentTownLifeEncounterState.Phase.class);
    private static final ConcurrentHashMap<String, LongAdder> VENUE_SELECTIONS =
            new ConcurrentHashMap<>();
    private static final LongAdder RESERVATION_ATTEMPTS = new LongAdder();
    private static final LongAdder RESERVATION_FAILURES = new LongAdder();
    private static final LongAdder NAVIGATION_ABANDONS = new LongAdder();
    private static final LongAdder ENCOUNTER_GROUPS = new LongAdder();
    private static final LongAdder ENCOUNTER_PARTICIPANTS = new LongAdder();
    private static final LongAdder FIDELITY_TRANSITIONS = new LongAdder();
    private static final AtomicInteger MAX_ENCOUNTER_SIZE = new AtomicInteger();

    private AgentTownLifeMetrics() {
    }

    static void activity(AgentTownLifeActivityEvent.Phase phase, String venueId) {
        ACTIVITY_PHASES.get(phase).increment();
        if (phase == AgentTownLifeActivityEvent.Phase.SELECTED
                && venueId != null && !venueId.isBlank()) {
            VENUE_SELECTIONS.computeIfAbsent(venueId, ignored -> new LongAdder()).increment();
        }
    }

    static void encounter(AgentTownLifeEncounterState.Phase phase) {
        ENCOUNTER_PHASES.get(phase).increment();
    }

    static void encounterGroup(int participants) {
        ENCOUNTER_GROUPS.increment();
        ENCOUNTER_PARTICIPANTS.add(Math.max(0, participants));
        MAX_ENCOUNTER_SIZE.accumulateAndGet(Math.max(0, participants), Math::max);
    }

    static void reservationAttempt(boolean successful) {
        RESERVATION_ATTEMPTS.increment();
        if (!successful) {
            RESERVATION_FAILURES.increment();
        }
    }

    static void navigationAbandon() {
        NAVIGATION_ABANDONS.increment();
    }

    static void fidelityTransition() {
        FIDELITY_TRANSITIONS.increment();
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                snapshot(AgentTownLifeActivityEvent.Phase.class, ACTIVITY_PHASES),
                snapshot(AgentTownLifeEncounterState.Phase.class, ENCOUNTER_PHASES),
                VENUE_SELECTIONS.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> entry.getValue().sum())),
                RESERVATION_ATTEMPTS.sum(), RESERVATION_FAILURES.sum(),
                NAVIGATION_ABANDONS.sum(), ENCOUNTER_GROUPS.sum(),
                ENCOUNTER_PARTICIPANTS.sum(), MAX_ENCOUNTER_SIZE.get(),
                FIDELITY_TRANSITIONS.sum());
    }

    private static <E extends Enum<E>> Map<E, LongAdder> counters(Class<E> type) {
        EnumMap<E, LongAdder> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            result.put(value, new LongAdder());
        }
        return result;
    }

    private static <E extends Enum<E>> Map<E, Long> snapshot(
            Class<E> type, Map<E, LongAdder> source) {
        EnumMap<E, Long> result = new EnumMap<>(type);
        source.forEach((key, value) -> result.put(key, value.sum()));
        return Map.copyOf(result);
    }

    public record Snapshot(
            Map<AgentTownLifeActivityEvent.Phase, Long> activityPhases,
            Map<AgentTownLifeEncounterState.Phase, Long> encounterPhases,
            Map<String, Long> venueSelections,
            long reservationAttempts,
            long reservationFailures,
            long navigationAbandons,
            long encounterGroups,
            long encounterParticipants,
            int maxEncounterSize,
            long fidelityTransitions) {
    }
}
