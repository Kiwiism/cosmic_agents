package server.agents.observer;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentLifecycleStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpectatorAgentSignalService {
    static final long STUCK_AFTER_MS = 15_000L;
    static final long STUCK_REPEAT_MS = 30_000L;
    static final long UPCOMING_COOLDOWN_MS = 3_000L;
    private static final int PROGRESS_DISTANCE_SQUARED = 64;
    private static final int TARGET_DISTANCE_SQUARED = 32 * 32;
    private static final Map<Integer, State> STATES = new HashMap<>();

    record Sample(int characterId,
                  int world,
                  int mapId,
                  String name,
                  int x,
                  int y,
                  boolean active,
                  boolean moving,
                  int targetRegion,
                  String decision) {
    }

    record Signal(SpectatorInterestService.Type type, int score, String detail) {
    }

    private SpectatorAgentSignalService() {
    }

    public static void sampleWorld(int world) {
        long now = System.currentTimeMillis();
        Set<Integer> seen = new HashSet<>();
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (agent == null
                    || agent.getWorld() != world
                    || !agent.isLoggedin()
                    || agent.getMap() == null) {
                continue;
            }
            seen.add(agent.getId());
            Point position = agent.getPosition();
            Point target = AgentNavigationDebugStateRuntime.navTargetPosition(entry);
            boolean following = AgentModeStateRuntime.following(entry);
            boolean moving = following
                    || (target != null
                        && position.distanceSq(target) > TARGET_DISTANCE_SQUARED);
            Sample sample = new Sample(
                    agent.getId(),
                    world,
                    agent.getMapId(),
                    agent.getName(),
                    position.x,
                    position.y,
                    AgentLifecycleStateRuntime.active(entry),
                    moving,
                    AgentNavigationDebugStateRuntime.navTargetRegionId(entry),
                    AgentNavigationDebugStateRuntime.decisionWithBlockReason(entry));
            for (Signal signal : evaluate(sample, now)) {
                SpectatorInterestService.publish(
                        agent, signal.type(), signal.score(), signal.detail());
            }
        }
        synchronized (STATES) {
            STATES.entrySet().removeIf(entry ->
                    entry.getValue().world == world && !seen.contains(entry.getKey()));
        }
    }

    static List<Signal> evaluate(Sample sample, long now) {
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(
                    sample.characterId(),
                    ignored -> new State(sample, now));
            List<Signal> signals = new ArrayList<>(3);

            boolean progressed = sample.mapId() != state.mapId
                    || distanceSquared(sample.x(), sample.y(), state.x, state.y)
                        >= PROGRESS_DISTANCE_SQUARED;
            if (progressed || !sample.active() || !sample.moving()) {
                state.lastProgressAt = now;
            }

            String signature = signature(sample);
            if (sample.active()
                    && sample.moving()
                    && !signature.isBlank()
                    && !signature.equals(state.upcomingSignature)
                    && now - state.lastUpcomingAt >= UPCOMING_COOLDOWN_MS) {
                signals.add(new Signal(
                        SpectatorInterestService.Type.UPCOMING,
                        65,
                        upcomingDetail(sample)));
                signals.add(new Signal(
                        SpectatorInterestService.Type.ROUTE,
                        25,
                        routeDetail(sample)));
                state.upcomingSignature = signature;
                state.lastUpcomingAt = now;
            }

            if (sample.active()
                    && sample.moving()
                    && now - state.lastProgressAt >= STUCK_AFTER_MS
                    && now - state.lastStuckAt >= STUCK_REPEAT_MS) {
                signals.add(new Signal(
                        SpectatorInterestService.Type.STUCK,
                        92,
                        "May be stuck while " + activity(sample)));
                state.lastStuckAt = now;
            }

            state.world = sample.world();
            state.mapId = sample.mapId();
            state.x = sample.x();
            state.y = sample.y();
            return List.copyOf(signals);
        }
    }

    static void resetForTests() {
        synchronized (STATES) {
            STATES.clear();
        }
    }

    private static int distanceSquared(int x1, int y1, int x2, int y2) {
        int deltaX = x1 - x2;
        int deltaY = y1 - y2;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private static String signature(Sample sample) {
        return sample.targetRegion() + ":" + safeDecision(sample.decision());
    }

    private static String upcomingDetail(Sample sample) {
        String decision = safeDecision(sample.decision());
        if (!decision.isBlank()) {
            return "About to " + decision;
        }
        return sample.targetRegion() >= 0
                ? "Navigating toward region " + sample.targetRegion()
                : "Starting a movement objective";
    }

    private static String activity(Sample sample) {
        String decision = safeDecision(sample.decision());
        if (!decision.isBlank()) {
            return decision;
        }
        return sample.targetRegion() >= 0
                ? "navigating to region " + sample.targetRegion()
                : "moving toward an objective";
    }

    private static String routeDetail(Sample sample) {
        String decision = safeDecision(sample.decision());
        if (!decision.isBlank()) {
            return "Route intent: " + decision;
        }
        return sample.targetRegion() >= 0
                ? "Route intent: region " + sample.targetRegion()
                : "Route intent updated";
    }

    private static String safeDecision(String decision) {
        if (decision == null) {
            return "";
        }
        String normalized = decision.trim();
        return normalized.length() <= 100
                ? normalized
                : normalized.substring(0, 100);
    }

    private static final class State {
        int world;
        int mapId;
        int x;
        int y;
        long lastProgressAt;
        long lastUpcomingAt;
        long lastStuckAt;
        String upcomingSignature = "";

        State(Sample sample, long now) {
            world = sample.world();
            mapId = sample.mapId();
            x = sample.x();
            y = sample.y();
            lastProgressAt = now;
        }
    }
}
