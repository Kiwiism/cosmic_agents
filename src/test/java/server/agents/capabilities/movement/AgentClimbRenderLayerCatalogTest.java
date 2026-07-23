package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentClimbRenderLayerCatalogTest {
    @Test
    void everyLithHarborLadderUsesForegroundClimbLayer() {
        Rope ladder = mock(Rope.class);
        when(ladder.isLadder()).thenReturn(true);
        when(ladder.x()).thenReturn(4_045);

        assertTrue(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(104_000_000, ladder));
        assertFalse(AgentClimbRenderLayerCatalog.usesClimbRenderLayer(100_000_000, ladder));
    }
}
