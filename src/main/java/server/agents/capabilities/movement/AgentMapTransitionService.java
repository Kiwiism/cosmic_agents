package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AgentMapTransitionService {
    @FunctionalInterface
    public interface TeleportAction {
        void teleport(AgentRuntimeEntry entry, Character agent, Point position);
    }

    @FunctionalInterface
    public interface SpawnFallInitializer {
        void begin(AgentRuntimeEntry entry, Character agent);
    }

    public record GroundingHooks(Function<MapleMap, Map<Integer, Foothold>> footholdIndexBuilder,
                                 BiFunction<MapleMap, Point, Point> groundPointFinder,
                                 TeleportAction teleporter,
                                 SpawnFallInitializer spawnFallInitializer,
                                 Consumer<AgentRuntimeEntry> afterTeleportReset,
                                 BiConsumer<MapleMap, AgentMovementProfile> graphWarmer,
                                 Consumer<AgentRuntimeEntry> movementBroadcaster) {
    }

    public record MapChangeHooks(GroundingHooks groundingHooks,
                                 BiPredicate<AgentRuntimeEntry, Character> requiresGrind,
                                 Consumer<AgentRuntimeEntry> issueGrind,
                                 BiPredicate<AgentRuntimeEntry, Character> requiresFollow,
                                 Consumer<AgentRuntimeEntry> issueFollow,
                                 Consumer<AgentRuntimeEntry> resetPartyQuestStage,
                                 BiConsumer<AgentRuntimeEntry, Character> shopMapChange,
                                 BiConsumer<AgentRuntimeEntry, Character> statusCheck) {
    }

    private AgentMapTransitionService() {
    }

    public static boolean groundAfterMapChange(AgentRuntimeEntry entry, Character agent, GroundingHooks hooks) {
        if (AgentMapStateRuntime.isTrackingMap(entry, agent.getMapId())) {
            return false;
        }

        MapleMap map = agent.getMap();
        AgentMapStateRuntime.setMapTracking(entry, agent.getMapId(), hooks.footholdIndexBuilder().apply(map));
        Point current = agent.getPosition();
        Portal entryPortal = map.findClosestPortal(current);
        AgentMapStateRuntime.setEntryPortalId(entry, entryPortal == null ? -1 : entryPortal.getId());
        Point ground = hooks.groundPointFinder().apply(map, new Point(current.x, current.y - 1));
        boolean spawnFall = AgentSpawnFallService.shouldFall(current, ground);
        hooks.teleporter().teleport(entry, agent, spawnFall || ground == null ? current : ground);
        hooks.afterTeleportReset().accept(entry);
        if (spawnFall) {
            hooks.spawnFallInitializer().begin(entry, agent);
        }
        hooks.graphWarmer().accept(map, AgentMovementStateRuntime.movementProfile(entry));
        hooks.movementBroadcaster().accept(entry);
        return true;
    }

    public static boolean handleTrackedMapChange(AgentRuntimeEntry entry, Character agent, MapChangeHooks hooks) {
        if (!groundAfterMapChange(entry, agent, hooks.groundingHooks())) {
            return false;
        }

        if (hooks.requiresGrind().test(entry, agent)) {
            hooks.issueGrind().accept(entry);
        } else if (hooks.requiresFollow().test(entry, agent)) {
            hooks.issueFollow().accept(entry);
        } else {
            hooks.resetPartyQuestStage().accept(entry);
        }
        hooks.shopMapChange().accept(entry, agent);
        hooks.statusCheck().accept(entry, agent);
        return true;
    }
}
