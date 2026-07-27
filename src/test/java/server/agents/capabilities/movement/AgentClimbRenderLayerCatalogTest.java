package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentClimbRenderLayerCatalogTest {
    @Test
    void laddersUseForegroundClimbLayerOutsideMapleIsland() {
        Rope ladder = mock(Rope.class);
        when(ladder.isLadder()).thenReturn(true);

        assertTrue(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(104_000_000, ladder));
        assertTrue(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(100_000_000, ladder));
    }

    @Test
    void mapleIslandLaddersKeepNativeLadderLayer() {
        Rope ladder = mock(Rope.class);
        when(ladder.isLadder()).thenReturn(true);

        assertFalse(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(10_000, ladder));
        assertFalse(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(1_010_100, ladder));
        assertFalse(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(2_000_000, ladder));
    }

    @Test
    void ropesAlwaysUseForegroundClimbLayer() {
        Rope rope = mock(Rope.class);
        when(rope.isLadder()).thenReturn(false);

        assertTrue(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(10_000, rope));
        assertTrue(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(104_000_000, rope));
    }

    @Test
    void missingClimbableDoesNotUseForegroundClimbLayer() {
        assertFalse(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(104_000_000, null));
    }
}
