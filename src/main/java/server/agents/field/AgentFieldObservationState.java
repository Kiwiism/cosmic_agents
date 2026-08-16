package server.agents.field;

import server.agents.operations.events.AgentCombatPostureChangedEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Bounded, presentation-neutral field timeline and per-posture time accounting. */
public final class AgentFieldObservationState {
    public static final AgentCapabilityStateKey<AgentFieldObservationState> STATE_KEY =
            new AgentCapabilityStateKey<>("field.observation", AgentFieldObservationState.class,
                    AgentFieldObservationState::new);
    private static final int MAX_TIMELINE = config.AgentTuning.intValue(
            "server.agents.field.AgentFieldObservationState.MAX_TIMELINE");

    public enum NarrationLevel { OFF, SUMMARY, VERBOSE }

    private NarrationLevel narrationLevel = NarrationLevel.SUMMARY;
    private String lifecycle = "IDLE";
    private AgentFieldRole role = AgentFieldRole.ROAMER;
    private AgentCombatPostureChangedEvent.Posture posture =
            AgentCombatPostureChangedEvent.Posture.IDLE;
    private long postureChangedAtMs;
    private final EnumMap<AgentCombatPostureChangedEvent.Posture, Long> postureTimeMs =
            new EnumMap<>(AgentCombatPostureChangedEvent.Posture.class);
    private long attacks;
    private long hitLines;
    private long missLines;
    private long damage;
    private long assignmentChanges;
    private long populationChanges;
    private long restTransitions;
    private int targetMobId;
    private Point targetPosition = new Point();
    private final ArrayDeque<TimelineEntry> timeline = new ArrayDeque<>();

    public synchronized void narrationLevel(NarrationLevel level) {
        narrationLevel = level == null ? NarrationLevel.SUMMARY : level;
    }

    public synchronized NarrationLevel narrationLevel() {
        return narrationLevel;
    }

    public synchronized void lifecycle(String next, String detail, long nowMs) {
        lifecycle = normalize(next, "IDLE");
        timeline("lifecycle", lifecycle, detail, nowMs);
    }

    public synchronized void assignment(AgentFieldRole nextRole, String detail, long nowMs) {
        role = nextRole == null ? AgentFieldRole.ROAMER : nextRole;
        assignmentChanges++;
        timeline("assignment", role.name(), detail, nowMs);
    }

    public synchronized void population(String change, String detail, long nowMs) {
        populationChanges++;
        timeline("population", change, detail, nowMs);
    }

    public synchronized void rest(String phase, String detail, long nowMs) {
        restTransitions++;
        timeline("rest", phase, detail, nowMs);
    }

    public synchronized void posture(
            AgentCombatPostureChangedEvent.Posture next,
            int nextTargetMobId,
            Point nextTargetPosition,
            String detail,
            long nowMs) {
        accruePosture(nowMs);
        posture = next == null ? AgentCombatPostureChangedEvent.Posture.IDLE : next;
        postureChangedAtMs = Math.max(0L, nowMs);
        targetMobId = Math.max(0, nextTargetMobId);
        targetPosition = nextTargetPosition == null ? new Point() : new Point(nextTargetPosition);
        timeline("posture", posture.name(), detail, nowMs);
    }

    public synchronized void attack(int hits, int misses) {
        attacks++;
        hitLines += Math.max(0, hits);
        missLines += Math.max(0, misses);
    }

    public synchronized void damage(int appliedDamage) {
        damage += Math.max(0, appliedDamage);
    }

    public synchronized Snapshot snapshot(long nowMs) {
        accruePosture(nowMs);
        return new Snapshot(narrationLevel, lifecycle, role, posture,
                Map.copyOf(postureTimeMs), attacks, hitLines, missLines, damage,
                assignmentChanges, populationChanges, restTransitions,
                targetMobId, new Point(targetPosition), List.copyOf(new ArrayList<>(timeline)));
    }

    private void accruePosture(long nowMs) {
        if (postureChangedAtMs <= 0L || nowMs <= postureChangedAtMs) {
            return;
        }
        postureTimeMs.merge(posture, nowMs - postureChangedAtMs, Long::sum);
        postureChangedAtMs = nowMs;
    }

    private void timeline(String domain, String value, String detail, long nowMs) {
        while (timeline.size() >= MAX_TIMELINE) {
            timeline.removeFirst();
        }
        timeline.addLast(new TimelineEntry(Math.max(0L, nowMs), domain,
                normalize(value, ""), normalize(detail, "")));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record TimelineEntry(long occurredAtMs, String domain, String value, String detail) {
    }

    public record Snapshot(
            NarrationLevel narrationLevel,
            String lifecycle,
            AgentFieldRole role,
            AgentCombatPostureChangedEvent.Posture posture,
            Map<AgentCombatPostureChangedEvent.Posture, Long> postureTimeMs,
            long attacks,
            long hitLines,
            long missLines,
            long damage,
            long assignmentChanges,
            long populationChanges,
            long restTransitions,
            int targetMobId,
            Point targetPosition,
            List<TimelineEntry> timeline) {
        public Snapshot {
            postureTimeMs = Map.copyOf(postureTimeMs);
            targetPosition = new Point(targetPosition);
            timeline = List.copyOf(timeline);
        }

        @Override
        public Point targetPosition() {
            return new Point(targetPosition);
        }
    }
}
