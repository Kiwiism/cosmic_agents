package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;
import server.maps.MapItem;

import java.io.IOException;
import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentHpqCoordinatorPolicyTest {
    @Test
    void hpqCoordinatorDoesNotUseTheFullMapPartyQuestLootVacuum() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "server", "agents",
                "capabilities", "partyquest", "hpq", "AgentHpqCoordinator.java"));

        assertFalse(source.contains("HPQ.lootNearby("));
        assertTrue(source.contains("AgentGrindLootStateRuntime.setObjectiveLootTarget"));
    }

    @Test
    void naturalSeedTargetsExcludePlantedPlayerDropsAndUnrelatedItems() {
        MapItem naturalSeed = drop(4_001_095, false, false);
        MapItem plantedSeed = drop(4_001_096, true, false);
        MapItem pickedSeed = drop(4_001_097, false, true);
        MapItem riceCake = drop(AgentHpqDefinition.RICE_CAKE, false, false);

        assertEquals(List.of(naturalSeed), AgentHpqCoordinator.naturalSeedDrops(
                List.of(naturalSeed, plantedSeed, pickedSeed, riceCake)));
    }

    @Test
    void onlySuccessfulTestRunsRemainVisibleForTheNextRun() {
        assertTrue(AgentHpqTestService.holdAfterTerminal(AgentHpqSession.Phase.COMPLETED));
        assertFalse(AgentHpqTestService.holdAfterTerminal(AgentHpqSession.Phase.FAILED));
    }

    @Test
    void completedObservationKeepsItsPartyTogether() {
        assertTrue(AgentHpqTerminationService.keepPartyAfterRelease(
                AgentHpqSession.Mode.TEST_OBSERVATION, true));
        assertFalse(AgentHpqTerminationService.keepPartyAfterRelease(
                AgentHpqSession.Mode.TEST_OBSERVATION, false));
        assertFalse(AgentHpqTerminationService.keepPartyAfterRelease(
                AgentHpqSession.Mode.PRODUCTION, true));
    }

    @Test
    void seedCollectorsAreDistributedAcrossTheFullSourceWidth() {
        assertEquals(3, AgentHpqCoordinator.distributedSourceIndex(0, 3, 18));
        assertEquals(9, AgentHpqCoordinator.distributedSourceIndex(1, 3, 18));
        assertEquals(15, AgentHpqCoordinator.distributedSourceIndex(2, 3, 18));
    }

    @Test
    void hpqDefenseReactionIsStaggeredButDeterministic() {
        assertEquals(0L, AgentHpqCoordinator.defenseReactionDelayMs(77L, 1001, 0, 2500L));
        long second = AgentHpqCoordinator.defenseReactionDelayMs(77L, 1002, 1, 2500L);
        long third = AgentHpqCoordinator.defenseReactionDelayMs(77L, 1003, 2, 2500L);

        assertTrue(second >= 450L && second <= 2500L);
        assertTrue(third >= 450L && third <= 2500L);
        assertEquals(second,
                AgentHpqCoordinator.defenseReactionDelayMs(77L, 1002, 1, 2500L));
    }

    @Test
    void defendersReceiveSeparateUpperGuardFootholdsAroundTheMoonBunny() {
        Point leader = AgentHpqDefinition.defenseGuardPoints().get(2);
        Point leftGuard = AgentHpqCoordinator.defenseGuardPoint(0, 2, true);
        Point rightGuard = AgentHpqCoordinator.defenseGuardPoint(1, 2, true);

        assertTrue(leader.y < AgentHpqDefinition.MOON_BUNNY_POSITION.y);
        assertTrue(leftGuard.y < -300 && rightGuard.y < -300);
        assertTrue(leftGuard.distance(rightGuard) > 500.0d);
        assertTrue(leftGuard.distance(leader) > 250.0d);
        assertTrue(rightGuard.distance(leader) > 250.0d);
    }

    private static MapItem drop(int itemId, boolean playerDrop, boolean pickedUp) {
        MapItem drop = mock(MapItem.class);
        when(drop.getItemId()).thenReturn(itemId);
        when(drop.isPlayerDrop()).thenReturn(playerDrop);
        when(drop.isPickedUp()).thenReturn(pickedUp);
        return drop;
    }
}
