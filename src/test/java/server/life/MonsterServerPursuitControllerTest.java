package server.life;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.integration.MonsterAggroTargetBridge;
import server.integration.MonsterAggroTargetProvider;
import server.maps.MapleMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MonsterServerPursuitControllerTest {
    @AfterEach
    void restoreNoopProvider() {
        MonsterAggroTargetBridge.install(null);
    }

    @Test
    void nativeControllerAssignmentPausesWhileServerPursuitOwnsMovement() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        Monster monster = new Monster(100100, stats);
        MapleMap map = mock(MapleMap.class);
        monster.setMap(map);
        MonsterAggroTargetBridge.install(new MonsterAggroTargetProvider() {
            @Override
            public boolean onAcceptedDamage(Monster candidate, Character attacker, int damage) {
                return false;
            }

            @Override
            public boolean usesServerPursuit(Monster candidate) {
                return candidate == monster;
            }
        });

        monster.aggroUpdateController();
        monster.aggroAutoAggroUpdate(mock(Character.class));

        verifyNoInteractions(map);
    }
}
