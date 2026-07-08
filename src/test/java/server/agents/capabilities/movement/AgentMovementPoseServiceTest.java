package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMovementPoseServiceTest {
    @Test
    void idleOnGroundPreservesLegacyPoseTransition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, true);

        AgentMovementPoseService.idleOnGround(entry, agent);

        assertFalse(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));
        verify(agent).setStance(CharacterStance.STAND_RIGHT_STANCE);
    }

    @Test
    void resolvedStandingCheckUsesCurrentPose() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentMovementPoseService.idleOnGround(entry, agent);

        assertTrue(AgentMovementPoseService.isStandingResolvedStance(entry));
    }
}
