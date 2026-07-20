package server.agents.progression;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentFirstJobJourneyRuntimeTest {
    @Test
    void completesSeededBiggsQuestAtOlafBeforeAnyTaxiTravel() {
        Character agent = beginner("BiggsReady", 9, 104000000);
        when(agent.getQuestStatus(1046)).thenReturn((byte) 1);
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1002101);
        when(gateway.canCompleteQuest(agent, 1046, 1002101)).thenReturn(true);
        when(gateway.completeQuest(agent, 1046, 1002101)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).completeQuest(agent, 1046, 1002101);
        verify(gateway, never()).runNpcScript(agent, 1002000);
    }

    @Test
    void startsCareerSpecificOlafPathFromAssignedBuildBundle() {
        Character agent = beginner("BowPath", 10, 104000000);
        when(agent.getQuestStatus(1046)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2081)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2078)).thenReturn((byte) 0);
        AgentRuntimeEntry entry = entry(agent, "bowman-standard-v1",
                AgentCareerProgressionState.Stage.START_CAREER_PATH);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1002101);
        when(gateway.canStartQuest(agent, 2078, 1002101)).thenReturn(true);
        when(gateway.startQuest(agent, 2078, 1002101)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).startQuest(agent, 2078, 1002101);
    }

    @Test
    void insufficientOlafVariantGrindsOnlyRightAroundLithHarborTargets() {
        Character agent = beginner("NeedsTen", 9, 104000100);
        when(agent.getQuestStatus(1046)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2081)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2077)).thenReturn((byte) 1);
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.GRIND_TO_JOB_LEVEL);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).grind(entry, Set.of(100100, 100101));
    }

    @Test
    void completesOlafCareerPathAtInstructorBeforeAdvancingJob() {
        Character agent = beginner("PathFirst", 10, 102000003);
        when(agent.getQuestStatus(1046)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2081)).thenReturn((byte) 2);
        when(agent.getQuestStatus(2077)).thenReturn((byte) 1);
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.COMPLETE_CAREER_PATH);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1022000);
        when(gateway.canCompleteQuest(agent, 2077, 1022000)).thenReturn(true);
        when(gateway.completeQuest(agent, 2077, 1022000)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).completeQuest(agent, 2077, 1022000);
        verify(gateway, never()).runNpcScript(agent, 1022000);
    }

    @Test
    void reloggedFirstJobAgentWaitsThenStartsItsRealInstructorQuest() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        when(agent.getName()).thenReturn("OldSchoolIGN");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getMapId()).thenReturn(103000003);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("thief-claw-standard-v1").orElseThrow();
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).assign(bundle);

        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, 2140)).thenReturn(0);
        when(gateway.npcPosition(agent, bundle.instructorNpcId())).thenReturn(new Point(10, 0));
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.canStartQuest(agent, 2140, bundle.instructorNpcId())).thenReturn(true);
        when(gateway.startQuest(agent, 2140, bundle.instructorNpcId())).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));
        assertEquals(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING,
                entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).stage());
        verify(gateway, never()).startQuest(agent, 2140, bundle.instructorNpcId());

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 3_200L, gateway));
        verify(gateway).startQuest(agent, 2140, bundle.instructorNpcId());
    }

    @Test
    void resetFixtureSchedulesInitialShopBeforeInstructorTraining() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        when(agent.getName()).thenReturn("PotionTrip");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getMapId()).thenReturn(103000003);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("thief-claw-standard-v1").orElseThrow();
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        state.reset(bundle, AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                AgentCareerProgressionState.Stage.ADVANCE_FIRST_JOB, 0L);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, mock(PrimitiveCapabilityGateway.class)));
        assertEquals(AgentCareerProgressionState.Stage.TRAVEL_TO_INITIAL_SHOP, state.stage());
    }

    @Test
    void completedMilestoneReturnsToInstructorBeforeCompleting() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(43);
        when(agent.getName()).thenReturn("ReturnHome");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(15);
        when(agent.getMapId()).thenReturn(103010000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("thief-claw-standard-v1").orElseThrow();
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        state.reset(bundle, AgentCareerProgressionState.RunMode.LEVEL15,
                AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE, 0L);
        state.trainingQuestIndex(4);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, mock(PrimitiveCapabilityGateway.class)));
        assertEquals(AgentCareerProgressionState.Stage.FINAL_RETURN_TO_INSTRUCTOR, state.stage());
    }

    private static Character beginner(String name, int level, int mapId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(Math.abs(name.hashCode()));
        when(agent.getName()).thenReturn(name);
        when(agent.getJob()).thenReturn(Job.BEGINNER);
        when(agent.getLevel()).thenReturn(level);
        when(agent.getMapId()).thenReturn(mapId);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        return agent;
    }

    private static AgentRuntimeEntry entry(Character agent,
                                           String bundleId,
                                           AgentCareerProgressionState.Stage stage) {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find(bundleId).orElseThrow();
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).reset(
                bundle, AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                "lv10", stage, 0L);
        return entry;
    }

    private static PrimitiveCapabilityGateway npcGateway(Character agent, int npcId) {
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.npcPosition(agent, npcId)).thenReturn(new Point(10, 0));
        when(gateway.grounded(agent)).thenReturn(true);
        return gateway;
    }
}
