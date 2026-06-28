package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotGrindTargetStateRuntimeTest {
    @Test
    void adaptsActiveGrindTarget() {
        BotEntry entry = new BotEntry(null, null, null);
        MapleMap map = mock(MapleMap.class);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);

        AgentBotGrindTargetStateRuntime.setTarget(entry, target);

        assertSame(target, AgentBotGrindTargetStateRuntime.target(entry));
        assertSame(target, AgentBotGrindTargetStateRuntime.activeTargetInMap(entry, map));
        assertNull(AgentBotGrindTargetStateRuntime.activeTargetInMap(entry, mock(MapleMap.class)));

        when(target.isAlive()).thenReturn(false);

        assertNull(AgentBotGrindTargetStateRuntime.activeTargetInMap(entry, map));
    }

    @Test
    void adaptsSeekRangeValidationAndClear() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Monster target = mock(Monster.class);
        when(bot.getMap()).thenReturn(map);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);
        when(target.getPosition()).thenReturn(new Point(130, 100));

        AgentBotGrindTargetStateRuntime.setTarget(entry, target);

        assertSame(target, AgentBotGrindTargetStateRuntime.targetInSeekRange(
                entry, bot, new Point(100, 100), 31 * 31));
        assertNull(AgentBotGrindTargetStateRuntime.targetInSeekRange(
                entry, bot, new Point(100, 100), 29 * 29));

        AgentBotGrindTargetStateRuntime.clear(entry);

        assertNull(AgentBotGrindTargetStateRuntime.target(entry));
    }
}
