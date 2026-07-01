package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotModeStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Agent-owned cross-map follow synchronization rule.
 */
public final class AgentFollowMapSyncService {
    @FunctionalInterface
    public interface ChangeMapAction {
        void changeMap(Character agent, MapleMap map, Point position);
    }

    public record FollowMapSyncHooks(BiFunction<MapleMap, Point, Point> groundPointFinder,
                                     BiConsumer<BotEntry, Character> idleOnGround,
                                     ChangeMapAction changeMap,
                                     Consumer<BotEntry> resetEntryState) {
    }

    private AgentFollowMapSyncService() {
    }

    public static boolean syncFollowMap(BotEntry entry,
                                        Character agent,
                                        Character followAnchor,
                                        FollowMapSyncHooks hooks) {
        if (!AgentBotModeStateRuntime.following(entry)
                || followAnchor == null
                || agent.getMapId() == followAnchor.getMapId()) {
            return false;
        }

        MapleMap targetMap = followAnchor.getMap();
        Point anchorPosition = followAnchor.getPosition();
        Point spawn = hooks.groundPointFinder().apply(targetMap, new Point(anchorPosition.x, anchorPosition.y - 1));
        if (spawn == null) {
            spawn = anchorPosition;
        }
        hooks.idleOnGround().accept(entry, agent);
        hooks.changeMap().changeMap(agent, targetMap, spawn);
        hooks.resetEntryState().accept(entry);
        return true;
    }
}
