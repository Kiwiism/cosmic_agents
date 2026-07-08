package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentShopStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentRecoveryTickServiceTest {
    @Test
    void runsFollowSyncBeforePartyAndTargetRecovery() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Character anchor = mock(Character.class);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentRecoveryTickService.tickRecovery(
                entry,
                agent,
                anchor,
                new Point(10, 20),
                hooks(calls, true, true, true));

        assertTrue(consumed);
        assertEquals(List.of("follow"), calls);
    }

    @Test
    void skipsFollowSyncDuringShopVisitAndRunsPartyRecovery() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentShopStateRuntime.startShopVisit(entry, new Point(1, 1), new Point(2, 2), 0, 1_000L);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentRecoveryTickService.tickRecovery(
                entry,
                mock(Character.class),
                mock(Character.class),
                new Point(10, 20),
                hooks(calls, true, true, true));

        assertTrue(consumed);
        assertEquals(List.of("party"), calls);
    }

    @Test
    void fallsThroughWhenAllRecoveryChecksFallThrough() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentRecoveryTickService.tickRecovery(
                entry,
                mock(Character.class),
                mock(Character.class),
                new Point(10, 20),
                hooks(calls, false, false, false));

        assertFalse(consumed);
        assertEquals(List.of("follow", "party", "target"), calls);
    }

    private static AgentRecoveryTickService.Hooks hooks(List<String> calls,
                                                        boolean followResult,
                                                        boolean partyResult,
                                                        boolean targetResult) {
        return new AgentRecoveryTickService.Hooks(
                (entry, agent, followAnchor) -> {
                    calls.add("follow");
                    return followResult;
                },
                (entry, agent, followAnchor) -> {
                    calls.add("party");
                    return partyResult;
                },
                (entry, agent, targetPosition) -> {
                    calls.add("target");
                    return targetResult;
                });
    }
}
