package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyGatherRegistryTest {
    private static final int OWNER = 4_100_001;
    private static final int MAP = 104040000;

    @Test
    void buffersJoinOneGatheringAndItClosesOnceTheyAllFinish() {
        long now = 1_000_000L;
        AgentPartyGatherRegistry.Gathering first = AgentPartyGatherRegistry.open(OWNER, MAP, now, 12_000, 25_000);
        AgentPartyGatherRegistry.Gathering second = AgentPartyGatherRegistry.open(OWNER, MAP, now + 500, 12_000, 25_000);
        assertSame(first, second);
        first.addBuffer(1);
        first.addBuffer(2);
        first.bufferFinished(1);
        assertFalse(first.buffersFinished());
        first.bufferFinished(2);
        assertTrue(first.buffersFinished());

        List<String> restored = new ArrayList<>();
        first.rememberRestore(7, () -> restored.add("grind"));
        first.rememberRestore(7, () -> restored.add("second snapshot ignored"));
        AgentPartyGatherRegistry.close(first).forEach(Runnable::run);
        assertEquals(List.of("grind"), restored);
        assertNull(AgentPartyGatherRegistry.active(OWNER, MAP, now));
        assertTrue(AgentPartyGatherRegistry.close(first).isEmpty());
    }

    @Test
    void theWaitForStragglersEndsBeforeTheGatheringDoes() {
        long now = 2_000_000L;
        AgentPartyGatherRegistry.Gathering gathering = AgentPartyGatherRegistry.open(OWNER + 1, MAP, now, 12_000, 25_000);
        assertTrue(gathering.waitingForParty(now + 11_999));
        assertFalse(gathering.waitingForParty(now + 12_000));
        assertSame(gathering, AgentPartyGatherRegistry.active(OWNER + 1, MAP, now + 24_999));
        // An expired gathering no longer counts, and a new request opens a fresh one.
        assertNull(AgentPartyGatherRegistry.active(OWNER + 1, MAP, now + 25_000));
        assertFalse(gathering == AgentPartyGatherRegistry.open(OWNER + 1, MAP, now + 25_000, 12_000, 25_000));
        assertNull(AgentPartyGatherRegistry.active(OWNER + 1, MAP + 1, now + 25_000));
    }

    @Test
    void onlyTheFirstWatcherClosesIt() {
        AgentPartyGatherRegistry.Gathering gathering = AgentPartyGatherRegistry.open(OWNER + 2, MAP, 0L, 1, 2);
        assertTrue(gathering.startWatching());
        assertFalse(gathering.startWatching());
    }

    @Test
    void anAgentSentToASpotIsStillWalkingUntilItArrivesOrTheWaitEnds() {
        AgentPartyGatherRegistry.Gathering gathering = AgentPartyGatherRegistry.open(OWNER + 3, MAP, 0L, 12_000, 25_000);
        gathering.rememberSpot(5, new Point(-357, 215), 60);
        assertTrue(gathering.stillWalking(5, new Point(250, 215), 1_000));
        assertFalse(gathering.stillWalking(5, new Point(-330, 215), 1_000));
        // Stuck somewhere: once the wait is over it buffs from where it stands.
        assertFalse(gathering.stillWalking(5, new Point(250, 215), 12_000));
        // An Agent that was not sent anywhere never waits to arrive.
        assertFalse(gathering.stillWalking(6, new Point(250, 215), 1_000));
        AgentPartyGatherRegistry.close(gathering);
    }
}
