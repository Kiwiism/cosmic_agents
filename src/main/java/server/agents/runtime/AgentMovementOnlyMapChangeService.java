package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentMapStateRuntime;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AgentMovementOnlyMapChangeService {
    private AgentMovementOnlyMapChangeService() {
    }

    public record Hooks(Function<MapleMap, Map<Integer, Foothold>> footholdIndexBuilder,
                        BiFunction<MapleMap, Point, Point> groundPointFinder,
                        TeleportAction teleporter,
                        Consumer<AgentRuntimeEntry> afterTeleportReset,
                        Consumer<AgentRuntimeEntry> movementBroadcaster,
                        BiConsumer<AgentRuntimeEntry, Character> shopMapChange,
                        BiConsumer<AgentRuntimeEntry, Character> statusCheck) {
    }

    @FunctionalInterface
    public interface TeleportAction {
        void teleport(AgentRuntimeEntry entry, Character agent, Point position);
    }

    public static boolean handleMapChange(AgentRuntimeEntry entry, Character agent, Hooks hooks) {
        if (AgentMapStateRuntime.isTrackingMap(entry, agent.getMapId())) {
            return false;
        }

        MapleMap map = agent.getMap();
        AgentMapStateRuntime.setMapTracking(entry, agent.getMapId(), hooks.footholdIndexBuilder().apply(map));
        Point current = agent.getPosition();
        Point ground = hooks.groundPointFinder().apply(map, new Point(current.x, current.y - 1));
        hooks.teleporter().teleport(entry, agent, ground != null ? ground : current);
        hooks.afterTeleportReset().accept(entry);
        hooks.movementBroadcaster().accept(entry);
        hooks.shopMapChange().accept(entry, agent);
        hooks.statusCheck().accept(entry, agent);
        return true;
    }
}
