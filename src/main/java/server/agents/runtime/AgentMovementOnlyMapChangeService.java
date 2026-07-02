package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotMapStateRuntime;
import server.bots.BotEntry;
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
                        Consumer<BotEntry> afterTeleportReset,
                        Consumer<BotEntry> movementBroadcaster,
                        BiConsumer<BotEntry, Character> shopMapChange,
                        BiConsumer<BotEntry, Character> statusCheck) {
    }

    @FunctionalInterface
    public interface TeleportAction {
        void teleport(BotEntry entry, Character agent, Point position);
    }

    public static boolean handleMapChange(BotEntry entry, Character agent, Hooks hooks) {
        if (AgentBotMapStateRuntime.isTrackingMap(entry, agent.getMapId())) {
            return false;
        }

        MapleMap map = agent.getMap();
        AgentBotMapStateRuntime.setMapTracking(entry, agent.getMapId(), hooks.footholdIndexBuilder().apply(map));
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
