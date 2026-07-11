package server.agents.capabilities.navigation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationGraphWarmupLifecycleTest {
    @AfterEach
    void restoreWarmups() {
        AgentNavigationGraphService.startAsyncWarmups();
    }

    @Test
    void shutdownInterruptsActiveWarmupBeforeMapDisposal() throws Exception {
        MapleMap map = mock(MapleMap.class);
        FootholdTree footholds = mock(FootholdTree.class);
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch probeInterrupted = new CountDownLatch(1);
        when(map.getId()).thenReturn(919_999_998);
        when(map.getFootholds()).thenReturn(footholds);
        when(footholds.getAllFootholds()).thenReturn(List.of());
        when(map.getRopes()).thenAnswer(ignored -> {
            probeStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                probeInterrupted.countDown();
            }
            return List.of();
        });

        AgentNavigationGraphService.startAsyncWarmups();
        AgentNavigationGraphService.warmGraphAsync(map, AgentMovementProfile.base());
        assertTrue(probeStarted.await(5, TimeUnit.SECONDS));
        assertEquals(1, AgentNavigationGraphService.pendingWarmupCount());

        AgentNavigationGraphService.shutdownAsyncWarmups();

        assertTrue(probeInterrupted.await(5, TimeUnit.SECONDS));
        assertEquals(0, AgentNavigationGraphService.pendingWarmupCount());
        assertFalse(AgentNavigationGraphService.asyncWarmupsRunning());

        AgentNavigationGraphService.warmGraphAsync(map, AgentMovementProfile.base());
        assertEquals(0, AgentNavigationGraphService.pendingWarmupCount());

        AgentNavigationGraphService.startAsyncWarmups();
        assertTrue(AgentNavigationGraphService.asyncWarmupsRunning());
    }
}
