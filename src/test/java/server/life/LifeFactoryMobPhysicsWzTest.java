package server.life;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeFactoryMobPhysicsWzTest {
    @Test
    void loadsRepresentativeSnailPhysicsWithoutDatabaseAccess() {
        DataProvider provider = DataProviderFactory.getDataProvider(WZFiles.MOB);
        Data mob = provider.getData("0100100.img");
        MonsterStats stats = new MonsterStats();

        LifeFactory.loadMonsterPhysicsStats(stats, mob, mob.getChildByPath("info"), null);

        assertEquals(-65, stats.getRawSpeed());
        assertEquals(0, stats.getRawFlySpeed());
        assertEquals(1, stats.getPushed());
        assertTrue(stats.isPhysicsMobile());
        assertFalse(stats.isPhysicsFlying());
        assertFalse(stats.canJump());
    }
}
