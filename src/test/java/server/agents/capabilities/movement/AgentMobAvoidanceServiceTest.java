package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AgentMobAvoidanceServiceTest {
    @Test
    void shouldJumpWhenBlockingMobIsInLaneAndLandingStaysInCurrentRegion() {
        MapleMap map = spy(new MapleMap(910009048, 0, 0, 910009048, 1.0f));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(300, 100), 1);
        map.setFootholds(footholds(foothold));
        AgentNavigationGraphService.rebuildGraph(map);
        doReturn(List.of(mockMob(new Point(130, 100), 100100))).when(map).getAllMonsters();

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertTrue(AgentMobAvoidanceService.shouldJumpToAvoidMob(entry, foothold, new Point(100, 100), 12));
    }

    @Test
    void shouldNotJumpWhenSimulatedLandingLeavesCurrentRegion() {
        MapleMap map = spy(new MapleMap(910009049, 0, 0, 910009049, 1.0f));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(140, 100), 1);
        map.setFootholds(footholds(foothold));
        AgentNavigationGraphService.rebuildGraph(map);
        doReturn(List.of(mockMob(new Point(120, 100), 100100))).when(map).getAllMonsters();

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertFalse(AgentMobAvoidanceService.shouldJumpToAvoidMob(entry, foothold, new Point(100, 100), 12));
    }

    private static FootholdTree footholds(Foothold foothold) {
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(foothold);
        return footholds;
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

    private static Monster mockMob(Point position, int id) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(position));
        when(mob.getId()).thenReturn(id);
        when(mob.isAlive()).thenReturn(true);
        when(mob.isFacingLeft()).thenReturn(false);
        return mob;
    }
}
