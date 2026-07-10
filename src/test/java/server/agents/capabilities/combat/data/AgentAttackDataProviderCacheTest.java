package server.agents.capabilities.combat.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentAttackDataProviderCacheTest {
    private static final int TEST_WEAPON_ID = 1302077;

    @Test
    void sharesOneProfileAcrossConcurrentReaders() throws Exception {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        provider.resetCachesForTest();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<AgentAttackDataProvider.NormalAttackProfile>> calls = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                calls.add(() -> provider.getNormalAttackProfile(TEST_WEAPON_ID));
            }

            AgentAttackDataProvider.NormalAttackProfile expected =
                    executor.invokeAll(calls).getFirst().get();
            assertNotNull(expected);
            for (var result : executor.invokeAll(calls)) {
                assertSame(expected, result.get());
            }
        }
    }

    @Test
    void resetAtomicallyReplacesProfileCache() {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        AgentAttackDataProvider.NormalAttackProfile before =
                provider.getNormalAttackProfile(TEST_WEAPON_ID);

        provider.resetCachesForTest();
        AgentAttackDataProvider.NormalAttackProfile after =
                provider.getNormalAttackProfile(TEST_WEAPON_ID);

        assertNotNull(before);
        assertNotNull(after);
        assertNotSame(before, after);
    }
}
