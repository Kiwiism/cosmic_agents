package server.observer;

import client.Character;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class ObserverInterestService {
    public enum Type {
        LEVEL_UP(0),
        QUEST_COMPLETE(1),
        JOB_ADVANCE(2),
        BOSS_DEFEAT(3),
        UPCOMING(4),
        STUCK(5),
        ROUTE(6);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public record Event(long sequence,
                        long timestamp,
                        int world,
                        int characterId,
                        int mapId,
                        String characterName,
                        Type type,
                        int score,
                        String detail) {
    }

    private static final int MAX_EVENTS_PER_WORLD = 2_048;
    private static final int MAX_RESPONSE_EVENTS = 64;
    private static final long MAX_AGE_MS = 5 * 60_000L;
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();
    private static final Map<Integer, ArrayDeque<Event>> EVENTS_BY_WORLD = new HashMap<>();

    private ObserverInterestService() {
    }

    public static void publish(Character character,
                               Type type,
                               int score,
                               String detail) {
        if (!ObserverFeature.enabled()
                || character == null
                || type == null
                || character.getMap() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Event event = new Event(
                NEXT_SEQUENCE.incrementAndGet(),
                now,
                character.getWorld(),
                character.getId(),
                character.getMapId(),
                bounded(character.getName(), 32),
                type,
                Math.max(0, Math.min(1_000, score)),
                bounded(detail, 160));
        synchronized (EVENTS_BY_WORLD) {
            ArrayDeque<Event> events = EVENTS_BY_WORLD.computeIfAbsent(
                    event.world(), ignored -> new ArrayDeque<>());
            events.addLast(event);
            trim(events, now);
        }
    }

    public static List<Event> eventsSince(int world, long afterSequence) {
        long now = System.currentTimeMillis();
        synchronized (EVENTS_BY_WORLD) {
            ArrayDeque<Event> events = EVENTS_BY_WORLD.get(world);
            if (events == null) {
                return List.of();
            }
            trim(events, now);
            List<Event> result = new ArrayList<>(Math.min(
                    MAX_RESPONSE_EVENTS, events.size()));
            for (Event event : events) {
                if (event.sequence() > afterSequence) {
                    result.add(event);
                    if (result.size() == MAX_RESPONSE_EVENTS) {
                        break;
                    }
                }
            }
            return List.copyOf(result);
        }
    }

    public static long latestSequence(int world) {
        synchronized (EVENTS_BY_WORLD) {
            ArrayDeque<Event> events = EVENTS_BY_WORLD.get(world);
            return events == null || events.isEmpty()
                    ? 0L
                    : events.getLast().sequence();
        }
    }

    static void resetForTests() {
        synchronized (EVENTS_BY_WORLD) {
            EVENTS_BY_WORLD.clear();
        }
    }

    private static void trim(ArrayDeque<Event> events, long now) {
        while (!events.isEmpty()
                && (events.size() > MAX_EVENTS_PER_WORLD
                    || now - events.getFirst().timestamp() > MAX_AGE_MS)) {
            events.removeFirst();
        }
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
