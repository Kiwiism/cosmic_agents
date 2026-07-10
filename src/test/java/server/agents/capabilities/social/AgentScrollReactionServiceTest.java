package server.agents.capabilities.social;

import client.Character;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.social.AgentScrollReactionRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentScrollReactionServiceTest {
    @Test
    void scrollReactionSchedulingUsesAgentScrollReactionRuntime() {
        MapleMap map = mock(MapleMap.class);
        Character source = mock(Character.class);
        when(source.getMap()).thenReturn(map);
        when(source.getMapId()).thenReturn(100000000);
        when(source.getId()).thenReturn(10);
        when(source.getPosition()).thenReturn(new Point(50, 50));

        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(20);
        when(bot.getMapId()).thenReturn(100000000);
        when(bot.getPosition()).thenReturn(new Point(60, 60));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        InventoryGateway inventory = mock(InventoryGateway.class);

        try (MockedStatic<AgentScrollReactionRuntime> scheduler = mockStatic(AgentScrollReactionRuntime.class)) {
            scheduler.when(() -> AgentScrollReactionRuntime.randomDelayMs(0, 2001)).thenReturn(123L);

            AgentScrollReactionService.handleScrollEvent(
                    source,
                    Equip.ScrollResult.SUCCESS,
                    0,
                    List.of(List.of(entry)),
                    inventory);

            scheduler.verify(() -> AgentScrollReactionRuntime.randomDelayMs(0, 2001));
            scheduler.verify(() -> AgentScrollReactionRuntime.afterDelay(eq(entry), eq(123L), any(Runnable.class)));
        }
    }

    @Test
    void resolvesScrollSuccessRateThroughInventoryGateway() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(inventory.getEquipStats(2040001)).thenReturn(Map.of("success", 60));

        assertEquals(60, AgentScrollReactionService.resolveScrollSuccessRate(2040001, inventory));
        assertEquals(0, AgentScrollReactionService.resolveScrollSuccessRate(0, inventory));
    }
}
