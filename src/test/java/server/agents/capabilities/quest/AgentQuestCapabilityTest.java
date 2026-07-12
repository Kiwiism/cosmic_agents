package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.integration.QuestGateway;

import java.awt.Point;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestCapabilityTest {
    @Test
    void startCapabilityValidatesAmherstQuestButDoesNotExecuteWithoutGateway() {
        AgentQuestStartCapability capability = new AgentQuestStartCapability();

        AgentQuestCapabilityResult result = capability.execute(startRequest(1031, 2101,
                AgentQuestSnapshot.emptyLv1Beginner()));

        assertEquals(AgentCapabilityStatus.NOT_READY, result.status());
        assertEquals(1031, result.questId());
        assertEquals("Heena and Sera", result.requirement().questName());
    }

    @Test
    void startCapabilityBlocksAlreadyStartedQuest() {
        AgentQuestStartCapability capability = new AgentQuestStartCapability();
        AgentQuestSnapshot snapshot = new AgentQuestSnapshot(1, 0,
                Map.of(1031, AgentQuestStatus.STARTED), Map.of(), Map.of(), Map.of());

        AgentQuestCapabilityResult result = capability.plan(startRequest(1031, 2101, snapshot));

        assertEquals(AgentCapabilityStatus.MISSING_REQUIREMENT, result.status());
        assertEquals("quest is not in NOT_STARTED state", result.message());
    }

    @Test
    void completeCapabilityRequiresStartedStateAndNeededItems() {
        AgentQuestCompleteCapability capability = new AgentQuestCompleteCapability();
        AgentQuestRequirement requirement = new AgentQuestRequirement(9000, "custom item turn in",
                100, 200, 1, 0, Set.of(), Set.of(), Map.of(4000000, 3),
                Map.of(), Map.of(), false);
        AgentQuestSnapshot missingItem = new AgentQuestSnapshot(10, 0,
                Map.of(9000, AgentQuestStatus.STARTED), Map.of(4000000, 2), Map.of(), Map.of());
        AgentQuestSnapshot ready = new AgentQuestSnapshot(10, 0,
                Map.of(9000, AgentQuestStatus.STARTED), Map.of(4000000, 3), Map.of(), Map.of());

        AgentQuestCapabilityResult blocked = capability.plan(request(9000, 200, missingItem, requirement));
        AgentQuestCapabilityResult allowed = capability.plan(request(9000, 200, ready, requirement));

        assertEquals(AgentCapabilityStatus.MISSING_REQUIREMENT, blocked.status());
        assertEquals(AgentCapabilityStatus.NOT_READY, allowed.status());
    }

    @Test
    void completeCapabilityAllowsAutoCompleteWithoutNpc() {
        AgentQuestCompleteCapability capability = new AgentQuestCompleteCapability();
        AgentQuestSnapshot snapshot = new AgentQuestSnapshot(1, 0,
                Map.of(1030, AgentQuestStatus.STARTED), Map.of(), Map.of(), Map.of());
        AgentQuestCapabilityRequest request = new AgentQuestCapabilityRequest(1030, 1000000, 0,
                null, null, 80, snapshot, null, true);

        AgentQuestCapabilityResult result = capability.plan(request);

        assertEquals(AgentCapabilityStatus.NOT_READY, result.status());
    }

    @Test
    void questCapabilityDelegatesOnlyWhenGatewayIsProvided() {
        AtomicReference<AgentQuestCapabilityRequest> executed = new AtomicReference<>();
        QuestGateway gateway = new QuestGateway() {
            @Override
            public AgentQuestCapabilityResult startQuest(AgentQuestCapabilityRequest request,
                    AgentQuestCapabilityResult plan) {
                executed.set(request);
                return AgentQuestCapabilityResult.success("started", request);
            }

            @Override
            public AgentQuestCapabilityResult completeQuest(AgentQuestCapabilityRequest request,
                    AgentQuestCapabilityResult plan) {
                return plan;
            }

            @Override
            public AgentQuestCapabilityResult forfeitQuest(AgentQuestCapabilityRequest request,
                    AgentQuestCapabilityResult plan) {
                return plan;
            }

            @Override
            public AgentQuestCapabilityResult resetQuest(AgentQuestCapabilityRequest request,
                    AgentQuestCapabilityResult plan) {
                return plan;
            }
        };
        AgentQuestStartCapability capability = new AgentQuestStartCapability(new AgentQuestRequirementValidator(),
                gateway);
        AgentQuestCapabilityRequest request = startRequest(1031, 2101, AgentQuestSnapshot.emptyLv1Beginner());

        AgentQuestCapabilityResult result = capability.execute(request);

        assertTrue(result.success());
        assertSame(request, executed.get());
    }

    @Test
    void scopePolicyBlocksLaterMapleIslandQuestForAmherstPhase() {
        AgentQuestStartCapability capability = new AgentQuestStartCapability();

        AgentQuestCapabilityResult result = capability.plan(startRequest(1028, 22000,
                AgentQuestSnapshot.emptyLv1Beginner()));

        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, result.status());
    }

    private static AgentQuestCapabilityRequest startRequest(int questId, int npcId, AgentQuestSnapshot snapshot) {
        return new AgentQuestCapabilityRequest(questId, 10000, npcId, new Point(0, 0),
                new Point(20, 0), 80, snapshot, null, true);
    }

    private static AgentQuestCapabilityRequest request(int questId, int npcId, AgentQuestSnapshot snapshot,
            AgentQuestRequirement requirement) {
        return new AgentQuestCapabilityRequest(questId, 10000, npcId, new Point(0, 0),
                new Point(20, 0), 80, snapshot, requirement, false);
    }
}
