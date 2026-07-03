package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGroundMovementRuntimeServiceTest {
    @Test
    void shouldMoveGroundedAgentTowardTarget() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(400, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        BotEntry entry = new BotEntry(bot, null, null);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(250, 100));

        assertTrue(bot.getPosition().x > 100);
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger();
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return bot;
    }
}
