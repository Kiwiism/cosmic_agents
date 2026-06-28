package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotShopStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotShopStateRuntimeTest {
    @Test
    void adaptsShopTransitionFlagsAndDelay() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentBotShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(0, AgentBotShopStateRuntime.shopApproachDelayMs(entry));

        entry.shopVisitPending = true;
        entry.shopSequenceActive = true;
        entry.shopApproachDelayMs = 250;

        assertTrue(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertTrue(AgentBotShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(250, AgentBotShopStateRuntime.shopApproachDelayMs(entry));
    }

    @Test
    void adaptsShopTargetsWithFallbackAndPointCopies() {
        BotEntry entry = new BotEntry(null, null, null);

        assertNull(AgentBotShopStateRuntime.activeShopTargetPosition(entry));

        entry.shopNpcPos = new Point(100, 200);

        Point npcPosition = AgentBotShopStateRuntime.shopNpcPosition(entry);
        npcPosition.x = 999;

        assertEquals(new Point(100, 200), AgentBotShopStateRuntime.shopNpcPosition(entry));
        assertEquals(new Point(100, 200), AgentBotShopStateRuntime.activeShopTargetPosition(entry));

        entry.shopTargetPos = new Point(300, 400);

        Point targetPosition = AgentBotShopStateRuntime.shopTargetPosition(entry);
        targetPosition.y = 888;

        assertEquals(new Point(300, 400), AgentBotShopStateRuntime.shopTargetPosition(entry));
        assertEquals(new Point(300, 400), AgentBotShopStateRuntime.activeShopTargetPosition(entry));
    }
}
