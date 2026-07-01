package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.integration.AgentBotMapStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

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
        void teleport(BotEntry entry, Character agent, Point position);
    }

    public record GroundingHooks(Function<MapleMap, Map<Integer, Foothold>> footholdIndexBuilder,
                                 BiFunction<MapleMap, Point, Point> groundPointFinder,
                                 TeleportAction teleporter,
                                 Consumer<BotEntry> afterTeleportReset,
                                 BiConsumer<MapleMap, AgentMovementProfile> graphWarmer,
                                 Consumer<BotEntry> movementBroadcaster) {
    }

    public record MapChangeHooks(GroundingHooks groundingHooks,
                                 BiPredicate<BotEntry, Character> requiresGrind,
                                 Consumer<BotEntry> issueGrind,
                                 BiPredicate<BotEntry, Character> requiresFollow,
                                 Consumer<BotEntry> issueFollow,
                                 Consumer<BotEntry> resetPartyQuestStage,
                                 BiConsumer<BotEntry, Character> shopMapChange,
                                 BiConsumer<BotEntry, Character> statusCheck) {
    }

    private AgentMapTransitionService() {
    }

    public static boolean groundAfterMapChange(BotEntry entry, Character agent, GroundingHooks hooks) {
        if (AgentBotMapStateRuntime.isTrackingMap(entry, agent.getMapId())) {
            return false;
        }

        MapleMap map = agent.getMap();
        AgentBotMapStateRuntime.setMapTracking(entry, agent.getMapId(), hooks.footholdIndexBuilder().apply(map));
        Point current = agent.getPosition();
        Point ground = hooks.groundPointFinder().apply(map, new Point(current.x, current.y - 1));
        hooks.teleporter().teleport(entry, agent, ground != null ? ground : current);
        hooks.afterTeleportReset().accept(entry);
        hooks.graphWarmer().accept(map, AgentBotMovementStateRuntime.movementProfile(entry));
        hooks.movementBroadcaster().accept(entry);
        return true;
    }

    public static boolean handleTrackedMapChange(BotEntry entry, Character agent, MapChangeHooks hooks) {
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
