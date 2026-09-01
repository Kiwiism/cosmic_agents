package server.agents.capabilities.mobcontrol;

import client.Character;
import net.server.channel.Channel;
import net.server.services.task.channel.ServerMobAutonomyService;
import net.server.services.type.ChannelServices;
import org.junit.jupiter.api.Test;
import server.integration.MobHitReactionContext;
import server.life.Monster;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMobReactionRouterTest {
    @Test
    void parsesModesCaseInsensitivelyAndRejectsInvalidStartupValue() {
        assertEquals(AgentMobReactionMode.OFF, AgentMobReactionMode.parse("off"));
        assertEquals(AgentMobReactionMode.PHYSICS, AgentMobReactionMode.parse("PHYSICS"));
        assertThrows(IllegalArgumentException.class, () -> AgentMobReactionMode.parse("both"));
    }

    @Test
    void eachModeResolvesToExactlyOneStrategy() {
        assertInstanceOf(OffMobReactionStrategy.class,
                AgentMobReactionRouter.strategy(AgentMobReactionMode.OFF));
        assertInstanceOf(PhysicsMobReactionStrategy.class,
                AgentMobReactionRouter.strategy(AgentMobReactionMode.PHYSICS));
    }

    @Test
    void nativeBossAuthorityBlocksCompetingAgentPhysicsAcquisition() {
        Character attacker = mock(Character.class);
        Monster monster = mock(Monster.class);
        MapleMap map = mock(MapleMap.class);
        Channel channel = mock(Channel.class);
        ServerMobAutonomyService autonomy = mock(ServerMobAutonomyService.class);
        when(monster.getMap()).thenReturn(map);
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(ChannelServices.MOB_AUTONOMY)).thenReturn(autonomy);
        when(autonomy.retainsNativeAuthority(monster)).thenReturn(true);

        AgentMobReactionRouter.acceptedHit(attacker, monster, 100,
                MobHitReactionContext.legacy(0L, attacker, monster));

        verify(autonomy).acquire(monster, attacker);
        verify(channel, never()).getServiceAccess(ChannelServices.MOB_PHYSICS);
    }

    @Test
    void stickyStationaryBossAuthorityAlsoBlocksAgentPhysics() {
        Character attacker = mock(Character.class);
        Monster monster = mock(Monster.class);
        MapleMap map = mock(MapleMap.class);
        Channel channel = mock(Channel.class);
        ServerMobAutonomyService autonomy = mock(ServerMobAutonomyService.class);
        when(monster.getMap()).thenReturn(map);
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(ChannelServices.MOB_AUTONOMY)).thenReturn(autonomy);
        when(autonomy.blocksAgentPhysics(monster)).thenReturn(true);

        AgentMobReactionRouter.acceptedHit(attacker, monster, 100,
                MobHitReactionContext.legacy(0L, attacker, monster));

        verify(autonomy).acquire(monster, attacker);
        verify(channel, never()).getServiceAccess(ChannelServices.MOB_PHYSICS);
    }
}
