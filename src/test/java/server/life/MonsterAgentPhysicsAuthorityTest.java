package server.life;

import client.BotClient;
import client.Character;
import client.Client;
import net.server.channel.Channel;
import net.server.services.task.channel.OverallService;
import org.junit.jupiter.api.Test;
import server.life.simulation.MobControlAuthority;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonsterAgentPhysicsAuthorityTest {
    @Test
    void agentAuthorityRejectsClientMovementAndControllerStealingThenHandsOff() {
        MapleMap map = mock(MapleMap.class);
        Channel channel = mock(Channel.class);
        OverallService overallService = mock(OverallService.class);
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(any())).thenReturn(overallService);
        Character agent = mock(Character.class);
        BotClient botClient = mock(BotClient.class);
        when(agent.getClient()).thenReturn(botClient);
        when(agent.getMap()).thenReturn(map);

        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        Monster monster = new Monster(100100, stats);
        monster.setMap(map);
        assertTrue(monster.aggroAcquireAgentPhysics(agent));
        assertSame(agent, monster.getController());
        assertEquals(MobControlAuthority.AGENT_PHYSICS, monster.getControlAuthority());

        Character real = mock(Character.class);
        Client realClient = mock(Client.class);
        when(real.getClient()).thenReturn(realClient);
        when(real.getMap()).thenReturn(map);
        when(real.isLoggedinWorld()).thenReturn(true);
        when(real.isAlive()).thenReturn(true);
        monster.aggroSwitchController(real, true);
        assertSame(agent, monster.getController());
        assertNull(monster.aggroMoveLifeUpdate(real));

        when(map.getCharacters()).thenReturn(List.of(real));
        assertTrue(monster.aggroReleaseAgentPhysics(agent, true));
        assertSame(real, monster.getController());
        assertEquals(MobControlAuthority.CLIENT, monster.getControlAuthority());
        verify(agent).stopControllingMonster(monster);
        verify(real).controlMonster(monster);
    }
}
