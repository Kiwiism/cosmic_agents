package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.movement.AgentClimbStateRuntime;

import server.agents.capabilities.movement.AgentMovementPhysicsStateRuntime;



import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentFormationStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeConfig;

import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.navigation.AgentNavigationMapLoader;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.mockito.stubbing.Answer;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.runtime.AgentMapStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentMovementTargetSideEffects;
import server.agents.runtime.AgentOwnerMotionStateRuntime;
import server.agents.runtime.AgentTickCadenceStateRuntime;
import server.agents.runtime.AgentTickStateRuntime;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentFormationRuntime;
import server.agents.runtime.AgentMovementOnlyStepRuntime;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BotMovementSimulationLab {
    private final Map<String, SimActor> actors = new LinkedHashMap<>();
    private final Map<String, AgentRuntimeEntry> bots = new LinkedHashMap<>();
    private final List<TraceFrame> trace = new ArrayList<>();
    private long elapsedMs = 0L;

    private BotMovementSimulationLab() {
    }

    static BotMovementSimulationLab loadMap(int mapId) {
        ensureWzPath();
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(mapId);
        return fromMap(map);
    }

    static BotMovementSimulationLab fromMap(MapleMap map) {
        AgentNavigationGraphService.rebuildGraph(map);
        return new BotMovementSimulationLab();
    }

    Character spawnActor(String name, int id, MapleMap map, Point startPosition) {
        Character actor = mockCharacter(name, id, map, startPosition);
        actors.put(name, new SimActor(actor));
        return actor;
    }

    AgentRuntimeEntry spawnBot(String name, int id, MapleMap map, Point startPosition) {
        Character bot = spawnActor(name, id, map, startPosition);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentTickCadenceStateRuntime.setSkipDelayMs(entry, 0);
        AgentMapStateRuntime.setMapTracking(entry, map.getId(), AgentFootholdIndexService.buildFhIndex(map));
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.fromCharacter(bot));
        bots.put(name, entry);
        return entry;
    }

    void setFollow(String botName, String ownerName) {
        AgentRuntimeEntry entry = requireBot(botName);
        Character owner = requireActor(ownerName);
        entry.setOwner(owner);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentModeStateRuntime.setGrinding(entry, false);
        refreshFormation(owner);
    }

    void clearFollow(String botName) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentModeStateRuntime.setFollowing(entry, false);
        entry.setOwner(null);
        AgentFormationStateRuntime.setFollowOffsetX(entry, 0);
    }

    void setMoveTarget(String botName, Point target, boolean precise) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, target, precise);
    }

    void clearMoveTarget(String botName) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
    }

    void teleport(String actorName, Point position) {
        Character actor = requireActor(actorName);
        actor.setPosition(new Point(position));
        AgentRuntimeEntry entry = bots.get(actorName);
        if (entry != null) {
            AgentMovementPoseService.teleportTo(entry, actor, position);
            AgentMovementStateResetService.resetEntryStateAfterTeleport(entry);
        }
    }

    void setFormation(String ownerName, AgentFormationService.FormationType type, int px, int snapRange) {
        Character owner = requireActor(ownerName);
        AgentFormationRuntime.setFormationState(owner, type, px, snapRange, followersOf(owner));
    }

    void setFollowOffset(String botName, int offsetX) {
        AgentFormationStateRuntime.setFollowOffsetX(requireBot(botName), offsetX);
    }

    void setAiAccumulator(String botName, int accumulatorMs) {
        AgentTickCadenceStateRuntime.setAiTickAccumulatorMs(requireBot(botName), accumulatorMs);
    }

    void primeMapState(String botName) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentMapStateRuntime.setMapTracking(
                entry,
                entry.bot().getMapId(),
                AgentFootholdIndexService.buildFhIndex(entry.bot().getMap()));
    }

    void attachBotToRope(String botName, Rope rope, int y) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentRopeMovementService.attachToRope(entry, entry.bot(), rope, y);
        primeMapState(botName);
    }

    void setNavState(String botName, AgentNavigationGraph.Edge edge, int targetRegionId, boolean preciseTarget) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, targetRegionId);
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, preciseTarget);
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, edge == null ? null : edge.startPoint);
    }

    void step(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            elapsedMs += AgentMovementPhysicsConfig.configuredMovementTickMs();

            List<PendingStep> pending = new ArrayList<>(bots.size());
            for (Map.Entry<String, AgentRuntimeEntry> AgentRuntimeEntry : bots.entrySet()) {
                AgentRuntimeEntry entry = AgentRuntimeEntry.getValue();
                boolean runAiTick = consumeAiTick(entry);
                AgentMovementTargetSnapshot targetSnapshot = AgentMovementTargetSideEffects.captureTargetSnapshot(entry);
                Point ownerPos = targetSnapshot.rawOwnerPosition();
                AgentTickStateRuntime.recordTick(entry, runAiTick, elapsedMs);
                AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, ownerPos);
                pending.add(new PendingStep(AgentRuntimeEntry.getKey(), entry, runAiTick, targetSnapshot));
            }

            for (PendingStep pendingStep : pending) {
                AgentMovementOnlyStepRuntime.stepMovementOnly(
                        pendingStep.entry(),
                        pendingStep.targetSnapshot().primaryTargetPosition(),
                        pendingStep.runAiTick());
                trace.add(TraceFrame.capture(
                        trace.size(),
                        elapsedMs,
                        pendingStep.name(),
                        pendingStep.entry(),
                        pendingStep.targetSnapshot()));
            }
        }
    }

    void stepRaw(String botName, Point targetPos, boolean runAiTick) {
        AgentRuntimeEntry entry = requireBot(botName);
        elapsedMs += AgentMovementPhysicsConfig.configuredMovementTickMs();
        AgentTickStateRuntime.recordTick(entry, runAiTick, elapsedMs);

        AgentMovementTargetSnapshot targetSnapshot = AgentMovementTargetSideEffects.captureTargetSnapshot(entry);
        Point ownerPos = targetSnapshot.rawOwnerPosition();
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, ownerPos);
        AgentMovementOnlyStepRuntime.stepMovementOnly(entry, new Point(targetPos), runAiTick);
        trace.add(TraceFrame.capture(trace.size(), elapsedMs, botName, entry,
                AgentMovementTargetSideEffects.captureTargetSnapshot(entry)));
    }

    Character actor(String name) {
        return requireActor(name);
    }

    AgentRuntimeEntry AgentRuntimeEntry(String name) {
        return requireBot(name);
    }

    Point position(String actorName) {
        return requireActor(actorName).getPosition();
    }

    List<String> formatRecentTrace(String botName, int limit) {
        List<String> lines = new ArrayList<>();
        int emitted = 0;
        for (int i = trace.size() - 1; i >= 0 && emitted < limit; i--) {
            TraceFrame frame = trace.get(i);
            if (!frame.botName().equals(botName)) {
                continue;
            }
            lines.add(0, frame.format());
            emitted++;
        }
        return lines;
    }

    String describeCurrentState(String botName) {
        AgentRuntimeEntry entry = requireBot(botName);
        AgentMovementTargetSnapshot snapshot = AgentMovementTargetSideEffects.captureTargetSnapshot(entry);
        return TraceFrame.capture(trace.size(), elapsedMs, botName, entry, snapshot).format();
    }

    List<String> botNames() {
        return new ArrayList<>(bots.keySet());
    }

    private void refreshFormation(Character owner) {
        List<AgentRuntimeEntry> followers = followersOf(owner);
        AgentFormationService.FormationState formation = followers.isEmpty()
                ? AgentFormationService.defaultStagger(AgentRuntimeConfig.cfg.FOLLOW_STAGGER, AgentMovementPhysicsConfig.configuredFollowYCap())
                : AgentFormationRuntime.formationStateFor(followers.getFirst());
        AgentFormationRuntime.setFormationState(owner, formation.type(), formation.px(), formation.snapRange(), followers);
    }

    private List<AgentRuntimeEntry> followersOf(Character owner) {
        return bots.values().stream()
                .filter(entry -> entry.owner() != null && entry.owner().getId() == owner.getId())
                .sorted(Comparator.comparing(entry -> entry.bot().getName()))
                .toList();
    }

    private Character requireActor(String name) {
        SimActor actor = actors.get(name);
        if (actor == null) {
            throw new IllegalArgumentException("Unknown actor: " + name);
        }
        return actor.character();
    }

    private AgentRuntimeEntry requireBot(String name) {
        AgentRuntimeEntry entry = bots.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown bot: " + name);
        }
        return entry;
    }

    private static boolean consumeAiTick(AgentRuntimeEntry entry) {
        AgentTickCadenceStateRuntime.setAiTickAccumulatorMs(entry,
                AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry)
                        + AgentMovementPhysicsConfig.configuredMovementTickMs());
        if (AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry) < AgentRuntimeConfig.cfg.AI_TICK_MS) {
            return false;
        }

        AgentTickCadenceStateRuntime.setAiTickAccumulatorMs(entry,
                AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry) - AgentRuntimeConfig.cfg.AI_TICK_MS);
        return true;
    }

    private static Character mockCharacter(String name, int id, MapleMap initialMap, Point startPosition) {
        Character character = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicReference<MapleMap> map = new AtomicReference<>(initialMap);
        AtomicInteger hp = new AtomicInteger(100);
        AtomicInteger stance = new AtomicInteger(0);

        when(character.getName()).thenReturn(name);
        when(character.getId()).thenReturn(id);
        when(character.getPosition()).thenAnswer((Answer<Point>) invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(character).setPosition(any(Point.class));
        when(character.getMap()).thenAnswer(invocation -> map.get());
        when(character.getMapId()).thenAnswer(invocation -> map.get().getId());
        when(character.getHp()).thenAnswer(invocation -> hp.get());
        when(character.getCurrentMaxHp()).thenReturn(100);
        when(character.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(character).setStance(anyInt());
        when(character.getJob()).thenReturn(Job.BEGINNER);
        when(character.getLevel()).thenReturn(1);
        when(character.getSkills()).thenReturn(Map.of());
        when(character.getRemainingSps()).thenReturn(new int[5]);
        when(character.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -11)).thenReturn(null);
        when(equipped.iterator()).thenReturn(Collections.emptyIterator());
        when(character.getTotalMoveSpeedStat()).thenReturn(100);
        when(character.getTotalJumpStat()).thenReturn(100);
        when(character.isLoggedinWorld()).thenReturn(true);
        doAnswer(invocation -> {
            map.set(invocation.getArgument(0));
            position.set(new Point(invocation.getArgument(1)));
            return null;
        }).when(character).changeMap(any(MapleMap.class), any(Point.class));
        return character;
    }

    private static void ensureWzPath() {
        if (System.getProperty("wz-path") == null) {
            System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
        }
    }

    private record SimActor(Character character) {
    }

    private record PendingStep(String name,
                               AgentRuntimeEntry entry,
                               boolean runAiTick,
                               AgentMovementTargetSnapshot targetSnapshot) {
    }

    private record TraceFrame(long index,
                              long elapsedMs,
                              String botName,
                              boolean runAiTick,
                              Point botPos,
                              Point ownerPos,
                              Point goalPos,
                              Point steeringPos,
                              String navDecision,
                              String physics,
                              String edge) {
        static TraceFrame capture(long index,
                                  long elapsedMs,
                                  String botName,
                                  AgentRuntimeEntry entry,
                                  AgentMovementTargetSnapshot targetSnapshot) {
            Point botPos = entry.bot().getPosition();
            Point ownerPos = targetSnapshot.rawOwnerPosition();
            Point goalPos = targetSnapshot.primaryTargetPosition();
            Point steeringPos = targetSnapshot.steeringTargetPosition();
            return new TraceFrame(
                    index,
                    elapsedMs,
                    botName,
                    AgentTickStateRuntime.lastTickWasAi(entry),
                    new Point(botPos),
                    new Point(ownerPos),
                    new Point(goalPos),
                    new Point(steeringPos),
                    AgentNavigationDebugStateRuntime.lastDecision(entry),
                    describePhysics(entry),
                    describeEdge((AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry)));
        }

        String format() {
            return String.format(
                    "[%05d +%4dms] ai=%s bot=%s owner=%s goal=%s steer=%s nav=%s phys=%s edge=%s",
                    index,
                    elapsedMs,
                    runAiTick ? "Y" : "N",
                    formatPoint(botPos),
                    formatPoint(ownerPos),
                    formatPoint(goalPos),
                    formatPoint(steeringPos),
                    navDecision,
                    physics,
                    edge);
        }

        private static String describePhysics(AgentRuntimeEntry entry) {
            if (AgentClimbStateRuntime.climbing(entry) && AgentClimbStateRuntime.climbRope(entry) != null) {
                return String.format("ROPE(x=%d top=%d bot=%d)", AgentClimbStateRuntime.climbRope(entry).x(), AgentClimbStateRuntime.climbRope(entry).topY(), AgentClimbStateRuntime.climbRope(entry).bottomY());
            }
            if (AgentMovementStateRuntime.inAir(entry)) {
                return String.format("AIR(velY=%.1f airVelX=%d)", AgentMovementPhysicsStateRuntime.verticalVelocity(entry), AgentMovementPhysicsStateRuntime.airVelocityX(entry));
            }
            return "GND";
        }

        private static String describeEdge(AgentNavigationGraph.Edge edge) {
            if (edge == null) {
                return "none";
            }
            return switch (edge.type) {
                case WALK -> String.format("WALK r%d->r%d %s->%s",
                        edge.fromRegionId, edge.toRegionId, formatPoint(edge.startPoint), formatPoint(edge.endPoint));
                case JUMP -> String.format("JUMP r%d->r%d %s->%s stepX=%d",
                        edge.fromRegionId, edge.toRegionId, formatPoint(edge.startPoint), formatPoint(edge.endPoint), edge.launchStepX);
                case DROP -> String.format("DROP r%d->r%d %s->%s stepX=%d",
                        edge.fromRegionId, edge.toRegionId, formatPoint(edge.startPoint), formatPoint(edge.endPoint), edge.launchStepX);
                case CLIMB -> String.format("CLIMB r%d->r%d %s->%s stepX=%d",
                        edge.fromRegionId, edge.toRegionId, formatPoint(edge.startPoint), formatPoint(edge.endPoint), edge.launchStepX);
                case PORTAL -> String.format("PORTAL r%d->r%d %s->%s",
                        edge.fromRegionId, edge.toRegionId, formatPoint(edge.startPoint), formatPoint(edge.endPoint));
            };
        }

        private static String formatPoint(Point point) {
            return "(" + point.x + "," + point.y + ")";
        }
    }
}
