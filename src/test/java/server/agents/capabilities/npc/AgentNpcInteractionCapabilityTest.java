package server.agents.capabilities.npc;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.integration.NpcGateway;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNpcInteractionCapabilityTest {
    @Test
    void validatesNpcInteractionButDoesNotExecuteWithoutGateway() {
        AgentNpcInteractionCapability capability = new AgentNpcInteractionCapability();
        AgentNpcInteractionRequest request = request(new Point(0, 0), new Point(20, 0), 80);

        AgentNpcInteractionResult result = capability.execute(request);

        assertEquals(AgentCapabilityStatus.NOT_READY, result.status());
        assertEquals(2101, result.npcId());
        assertEquals(new Point(20, 0), result.npcPosition());
        assertEquals(800L, result.estimatedDelayMs());
    }

    @Test
    void blocksNpcInteractionWhenAgentIsOutOfRange() {
        AgentNpcInteractionCapability capability = new AgentNpcInteractionCapability();

        AgentNpcInteractionResult result = capability.plan(request(new Point(0, 0), new Point(200, 0), 80));

        assertEquals(AgentCapabilityStatus.NOT_READY, result.status());
        assertEquals("agent is outside NPC interaction range", result.message());
    }

    @Test
    void delegatesOnlyWhenGatewayIsProvided() {
        AtomicReference<AgentNpcInteractionRequest> executedRequest = new AtomicReference<>();
        NpcGateway gateway = (request, plan) -> {
            executedRequest.set(request);
            return AgentNpcInteractionResult.success("queued", request, plan.npcPosition(), plan.approachPoint(),
                    plan.estimatedDelayMs());
        };
        AgentNpcInteractionCapability capability = new AgentNpcInteractionCapability(new AgentNpcInteractionValidator(),
                gateway);
        AgentNpcInteractionRequest request = request(new Point(0, 0), new Point(20, 0), 80);

        AgentNpcInteractionResult result = capability.execute(request);

        assertTrue(result.success());
        assertEquals(AgentCapabilityStatus.SUCCESS, result.status());
        assertSame(request, executedRequest.get());
    }

    @Test
    void missingNpcIdIsRejectedBeforeAnyRuntimeMutation() {
        AgentNpcInteractionCapability capability = new AgentNpcInteractionCapability();
        AgentNpcInteractionRequest request = new AgentNpcInteractionRequest(10000, 0, 1000,
                AgentNpcInteractionType.QUEST_START, new Point(0, 0), new Point(0, 0), 80,
                false, null, 1L);

        AgentNpcInteractionResult result = capability.plan(request);

        assertEquals(AgentCapabilityStatus.MISSING_REQUIREMENT, result.status());
        assertNotNull(result.message());
    }

    private static AgentNpcInteractionRequest request(Point agentPosition, Point npcPosition, int maxRangePx) {
        return new AgentNpcInteractionRequest(10000, 2101, 1000, AgentNpcInteractionType.QUEST_START,
                agentPosition, npcPosition, maxRangePx, false, null, 1L);
    }
}
