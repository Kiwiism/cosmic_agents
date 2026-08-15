package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentRangedTacticalStateRuntimeTest {
    @Test
    void relatedCommitmentsShareOneCapabilityStateOwner() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        AgentRetreatHoldStateRuntime.setHold(entry, new Point(10, 20), 1_000L);
        AgentBreakoutStateRuntime.setBreakoutCommitment(entry, -1, 2_000L);
        AgentAoeRepositionStateRuntime.setAnchor(entry, new Point(30, 40), 3_000L);

        assertEquals(1, entry.capabilityStates().size());
        assertTrue(entry.capabilityStates().registeredStateIds()
                .contains(AgentRangedTacticalState.STATE_KEY.id()));
    }
}
