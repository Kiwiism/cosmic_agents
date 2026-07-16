package server.life;

import client.BotClient;
import client.Character;
import client.Client;
import net.server.coordinator.world.MonsterAggroCoordinator;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import tools.Pair;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
class MonsterControllerAssignmentHoldTest {
    @Test
    void botDamageDoesNotTemporarilyStealRealClientControl() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        TestMonster monster = new TestMonster(100100, stats);
        MapleMap map = mock(MapleMap.class);
        MonsterAggroCoordinator aggro = mock(MonsterAggroCoordinator.class);
        when(map.getAggroCoordinator()).thenReturn(aggro);
        monster.setMap(map);

        Character realController = controllerOn(map);
        Client realClient = realController.getClient();
        monster.aggroSwitchController(realController, false);
        reset(realClient);

        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        monster.aggroMonsterDamage(agent, 10);

        assertSame(realController, monster.getController());
        verify(realClient, never()).sendPacket(any(Packet.class));
        verify(agent, never()).controlMonster(monster);
        verify(aggro).addAggroDamage(monster, 0, 10);
    }

    @Test
    void blocksControllerAssignmentUntilHoldIsReleased() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        TestMonster monster = new TestMonster(100100, stats);
        MapleMap map = mock(MapleMap.class);
        monster.setMap(map);

        Character candidate = mock(Character.class);
        Client client = mock(Client.class);
        when(candidate.isLoggedinWorld()).thenReturn(true);
        when(candidate.getMap()).thenReturn(map);
        when(candidate.getClient()).thenReturn(client);

        assertNotNull(monster.aggroCommitIfHeadless(10_000L, () -> {
        }));

        monster.aggroSwitchController(candidate, true);

        assertNull(monster.getController());
        verify(client, never()).sendPacket(any(Packet.class));

        monster.aggroReleaseControllerAssignmentHold();
        monster.aggroSwitchController(candidate, true);

        assertSame(candidate, monster.getController());
        verify(client).sendPacket(any(Packet.class));
        verify(candidate).controlMonster(monster);
    }

    @Test
    void syntheticReactionSuspendsExistingControllerAndBlocksReassignment() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        TestMonster monster = new TestMonster(100100, stats);
        MapleMap map = mock(MapleMap.class);
        monster.setMap(map);

        Character controller = controllerOn(map);
        Character replacement = controllerOn(map);
        monster.aggroSwitchController(controller, true);

        Pair<Character, Boolean> suspended =
                monster.aggroSuspendControllerForSyntheticReaction(10_000L);

        assertSame(controller, suspended.getLeft());
        assertNull(monster.getController());
        monster.aggroSwitchController(replacement, true);
        assertNull(monster.getController());

        monster.aggroReleaseControllerAssignmentHold();
        monster.aggroSwitchController(replacement, true);
        assertSame(replacement, monster.getController());
    }

    @Test
    void departingControllerIsClearedWithoutOldFieldStopPacket() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        TestMonster monster = new TestMonster(100100, stats);
        MapleMap map = mock(MapleMap.class);
        monster.setMap(map);

        Character controller = controllerOn(map);
        Client client = controller.getClient();
        monster.aggroSwitchController(controller, false);
        reset(client);
        when(controller.isChangingMaps()).thenReturn(true);

        Pair<Character, Boolean> removed = monster.aggroRemoveController();

        assertSame(controller, removed.getLeft());
        assertNull(monster.getController());
        verify(client, never()).sendPacket(any(Packet.class));
        verify(controller).stopControllingMonster(monster);
    }

    private static Character controllerOn(MapleMap map) {
        Character controller = mock(Character.class);
        when(controller.isLoggedinWorld()).thenReturn(true);
        when(controller.getMap()).thenReturn(map);
        when(controller.getClient()).thenReturn(mock(Client.class));
        return controller;
    }

    private static final class TestMonster extends Monster {
        private TestMonster(int id, MonsterStats stats) {
            super(id, stats);
        }

        @Override
        public void aggroUpdatePuppetVisibility() {
            // Keep this controller-lock test independent of channel services.
        }
    }
}
