package server.agents.runtime.activity.control;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacadeRegistry;
import server.agents.runtime.activity.session.AgentActivityTransferPort;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.maps.MapleMap;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStandardWorldActivityLifecycleHandlerTest {
    @Test
    void retreatsThroughNormalTravelWhenNoSafeSpotAppearsBeforeDeadline() {
        Character agent = mock(Character.class);
        MapleMap field = mock(MapleMap.class);
        MapleMap returnMap = mock(MapleMap.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getMap()).thenReturn(field);
        when(agent.getMapId()).thenReturn(100000001);
        when(agent.getPosition()).thenReturn(null);
        when(field.getReturnMap()).thenReturn(returnMap);
        when(returnMap.getId()).thenReturn(100000000);
        AtomicInteger transfers = new AtomicInteger();
        AgentStandardWorldActivityLifecycleHandler handler =
                new AgentStandardWorldActivityLifecycleHandler(
                        mock(AgentLiveActivityFacadeRegistry.class),
                        (entry, character, destinationMapId, nowMs) -> {
                            transfers.incrementAndGet();
                            assertEquals(100000000, destinationMapId);
                            return AgentActivityTransferPort.Result.pending(
                                    "walking through portals", nowMs + 500L);
                        });
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        var result = handler.advance(suspend(),
                AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.MANUAL, 1_000L),
                entry, agent, null, "", 1_200L);

        assertEquals(AgentWorldActivityLifecycleHandler.Result.Status.PROGRESSED,
                result.status());
        assertEquals(1, transfers.get());
        assertTrue(result.reason().contains("retreating normally"));
    }

    private static AgentWorldDirective suspend() {
        return new AgentWorldDirective(
                1, "suspend-to-safe-place", 27,
                AgentWorldDirectiveType.SUSPEND_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, null, null, "", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                100, 1_000L, 1_100L, "operator requested pause");
    }
}
