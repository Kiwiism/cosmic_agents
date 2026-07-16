package server.agents.runtime;

import server.agents.capabilities.combat.AgentMonsterControlService;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.simulation.MobControlAuthority;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMonsterControlServiceTest {
    @Test
    void ignoresAgentsWithoutControlledMonsters() {
        Character agent = mock(Character.class);
        when(agent.getControlledMonsters()).thenReturn(List.of());

        AgentMonsterControlService.releaseControlledMonsters(agent);
    }

    @Test
    void releasesLegacyControlledMonstersButPreservesPhysicsAuthority() {
        Character agent = mock(Character.class);
        Monster first = mock(Monster.class);
        Monster physicsOwned = mock(Monster.class);
        when(physicsOwned.getControlAuthority()).thenReturn(MobControlAuthority.AGENT_PHYSICS);
        when(agent.getControlledMonsters()).thenReturn(List.of(first, physicsOwned));

        AgentMonsterControlService.releaseControlledMonsters(agent);

        verify(first).aggroRedirectController();
        verify(physicsOwned, never()).aggroRedirectController();
    }
}
