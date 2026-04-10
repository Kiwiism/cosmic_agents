package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotNavigationGraphFallbackTest {
    @Test
    void shouldUseClosestCachedGraphBeforeHeuristicFallback() {
        MapleMap map = new MapleMap(910000042, 0, 0, 910000042, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(100, 100), 1));
        footholds.insert(new Foothold(new Point(106, 160), new Point(280, 160), 2));
        map.setFootholds(footholds);

        BotNavigationGraph cachedGraph = BotNavigationGraphProvider.rebuildGraph(map, BotMovementProfile.base());
        assertNotNull(cachedGraph);

        Character bot = mockBot(new Point(60, 100), map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.movementProfile = new BotMovementProfile(125, 110);

        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, new Point(180, 160), true);

        assertFalse(entry.graphWarmupFallback, "closest cached graph should be used before heuristics");
        assertFalse(directive.consumedTick && entry.navEdge == null && "graph-warmup".equals(entry.lastNavDecision),
                "cached fallback graph should route with a graph edge, not heuristic warmup");
        assertNotNull(entry.navEdge, "cached fallback graph should provide a real nav edge");
        assertFalse("graph-warmup".equals(entry.lastNavDecision),
                "nav should stay on graph-based routing instead of dropping straight into heuristics");
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return bot;
    }
}
