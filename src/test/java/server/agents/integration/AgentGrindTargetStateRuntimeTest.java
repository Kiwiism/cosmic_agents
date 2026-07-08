package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindTargetStateRuntimeTest {
    @Test
    void adaptsActiveGrindTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        MapleMap map = mock(MapleMap.class);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);

        AgentGrindTargetStateRuntime.setTarget(entry, target);

        assertSame(target, AgentGrindTargetStateRuntime.target(entry));
        assertSame(target, AgentGrindTargetStateRuntime.activeTargetInMap(entry, map));
        assertNull(AgentGrindTargetStateRuntime.activeTargetInMap(entry, mock(MapleMap.class)));

        when(target.isAlive()).thenReturn(false);

        assertNull(AgentGrindTargetStateRuntime.activeTargetInMap(entry, map));
    }

    @Test
    void adaptsSeekRangeValidationAndClear() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Monster target = mock(Monster.class);
        when(bot.getMap()).thenReturn(map);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);
        when(target.getPosition()).thenReturn(new Point(130, 100));

        AgentGrindTargetStateRuntime.setTarget(entry, target);

        assertSame(target, AgentGrindTargetStateRuntime.targetInSeekRange(
                entry, bot, new Point(100, 100), 31 * 31));
        assertNull(AgentGrindTargetStateRuntime.targetInSeekRange(
                entry, bot, new Point(100, 100), 29 * 29));

        AgentGrindTargetStateRuntime.clear(entry);

        assertNull(AgentGrindTargetStateRuntime.target(entry));
    }
}
