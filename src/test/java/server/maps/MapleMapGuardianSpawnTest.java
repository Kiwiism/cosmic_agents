package server.maps;

import org.junit.jupiter.api.Test;
import server.partyquest.GuardianSpawnPoint;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MapleMapGuardianSpawnTest {
    @Test
    void shouldReturnNullWhenNoCompatibleGuardianSpawnExists() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        GuardianSpawnPoint taken = spawn(0, true);
        GuardianSpawnPoint wrongTeam = spawn(1, false);
        map.addGuardianSpawnPoint(taken);
        map.addGuardianSpawnPoint(wrongTeam);

        assertNull(map.getRandomGuardianSpawn(0));
    }

    @Test
    void shouldReturnOnlyCompatibleUntakenSpawn() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        GuardianSpawnPoint compatible = spawn(0, false);
        map.addGuardianSpawnPoint(spawn(1, false));
        map.addGuardianSpawnPoint(compatible);

        assertSame(compatible, map.getRandomGuardianSpawn(0));
    }

    private GuardianSpawnPoint spawn(int team, boolean taken) {
        GuardianSpawnPoint spawn = new GuardianSpawnPoint(new Point());
        spawn.setTeam(team);
        spawn.setTaken(taken);
        return spawn;
    }
}
