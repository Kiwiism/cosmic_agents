package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentAnchoredFarmModeTickServiceTest {
    @Test
    void fallsThroughWhenNoFarmAnchorExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AtomicInteger ticks = new AtomicInteger();

        boolean consumed = AgentAnchoredFarmModeTickService.tickIfAnchoredFarm(
                entry,
                agent,
                new Point(1, 2),
                true,
                new AgentAnchoredFarmModeTickService.Hooks((farmEntry, farmAgent, farmPosition, runAiTick) ->
                        ticks.incrementAndGet()));

        assertFalse(consumed);
        assertEquals(0, ticks.get());
    }

    @Test
    void runsAnchoredFarmTickWhenAnchorExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 50), 1000);
        AtomicInteger ticks = new AtomicInteger();

        boolean consumed = AgentAnchoredFarmModeTickService.tickIfAnchoredFarm(
                entry,
                agent,
                new Point(1, 2),
                false,
                new AgentAnchoredFarmModeTickService.Hooks((farmEntry, farmAgent, farmPosition, runAiTick) -> {
                    ticks.incrementAndGet();
                    assertEquals(new Point(1, 2), farmPosition);
                    assertFalse(runAiTick);
                }));

        assertTrue(consumed);
        assertEquals(1, ticks.get());
    }
}
