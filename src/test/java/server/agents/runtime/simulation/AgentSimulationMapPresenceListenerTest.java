package server.agents.runtime.simulation;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSimulationMapPresenceListenerTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void observationTransitionWakesEachActiveAgentInMap() {
        Character firstAgent = mock(Character.class);
        when(firstAgent.getId()).thenReturn(101);
        Character secondAgent = mock(Character.class);
        when(secondAgent.getId()).thenReturn(102);
        AgentRuntimeRegistry.registerEntry(1, new AgentRuntimeEntry(firstAgent, null, null));
        AgentRuntimeRegistry.registerEntry(1, new AgentRuntimeEntry(secondAgent, null, null));
        MapleMap map = mock(MapleMap.class);
        when(map.getAllPlayers()).thenReturn(List.of(firstAgent, secondAgent));
        AtomicInteger wakes = new AtomicInteger();
        AgentSimulationMapPresenceListener listener = new AgentSimulationMapPresenceListener(
                () -> true,
                entry -> {
                    wakes.incrementAndGet();
                    return true;
                });

        listener.observationChanged(map, true);

        assertEquals(2, wakes.get());
    }

    @Test
    void disabledSimulationDoesNotScanOrWakeMapAgents() {
        MapleMap map = mock(MapleMap.class);
        AtomicInteger wakes = new AtomicInteger();
        AgentSimulationMapPresenceListener listener = new AgentSimulationMapPresenceListener(
                () -> false,
                entry -> {
                    wakes.incrementAndGet();
                    return true;
                });

        listener.observationChanged(map, true);

        assertEquals(0, wakes.get());
    }

    @Test
    void oneRejectedWakeDoesNotStopOtherMapAgents() {
        Character firstAgent = mock(Character.class);
        when(firstAgent.getId()).thenReturn(101);
        Character secondAgent = mock(Character.class);
        when(secondAgent.getId()).thenReturn(102);
        AgentRuntimeRegistry.registerEntry(1, new AgentRuntimeEntry(firstAgent, null, null));
        AgentRuntimeRegistry.registerEntry(1, new AgentRuntimeEntry(secondAgent, null, null));
        MapleMap map = mock(MapleMap.class);
        when(map.getAllPlayers()).thenReturn(List.of(firstAgent, secondAgent));
        AtomicInteger wakes = new AtomicInteger();
        AgentSimulationMapPresenceListener listener = new AgentSimulationMapPresenceListener(
                () -> true,
                entry -> {
                    if (entry.bot().getId() == 101) {
                        throw new IllegalStateException("rejected wake");
                    }
                    wakes.incrementAndGet();
                    return true;
                });

        listener.observationChanged(map, true);

        assertEquals(1, wakes.get());
    }
}
