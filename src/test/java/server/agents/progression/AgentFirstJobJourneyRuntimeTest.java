package server.agents.progression;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentFirstJobJourneyRuntimeTest {
    @Test
    void taxiArrivalWaitsInElliniaBeforeStartingTheLibraryRoute() {
        Character agent = beginner("VisibleEllinia", 10, 104000000);
        AtomicInteger mapId = new AtomicInteger(104000000);
        when(agent.getMapId()).thenAnswer(ignored -> mapId.get());
        when(agent.getQuestStatus(org.mockito.ArgumentMatchers.anyInt())).thenReturn((byte) 2);
        AgentRuntimeEntry entry = entry(agent, "magician-standard-v1",
                AgentCareerProgressionState.Stage.TAKE_TAXI);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1002000);
        when(gateway.runNpcScript(agent, 1002000,
                AgentTaxiDialogueSequence.lithHarborPhil(2))).thenAnswer(ignored -> {
                    mapId.set(101000000);
                    return true;
                });

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        assertEquals(101000000, agent.getMapId());
        assertEquals(AgentCareerProgressionState.Stage.ENTER_INSTRUCTOR_ROOM, state.stage());
        assertFalse(state.ready(100L));
        verify(gateway).stop(entry);
        verify(gateway, never()).enterPortal(agent, 26);
    }

    @Test
    void libraryArrivalPauseCannotBeSkippedByCareerReconciliation() {
        Character agent = beginner("LibraryDoor", 10, 101000003);
        when(agent.getQuestStatus(org.mockito.ArgumentMatchers.anyInt())).thenReturn((byte) 2);
        AgentRuntimeEntry entry = entry(agent, "magician-standard-v1",
                AgentCareerProgressionState.Stage.ENTER_INSTRUCTOR_ROOM);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        long nowMs = System.currentTimeMillis();
        entry.capabilityStates().require(AgentVictoriaRouteState.STATE_KEY)
                .recordPortalSuccess(101000003, nowMs, 1_500L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, nowMs + 500L, gateway));

        assertEquals(AgentCareerProgressionState.Stage.ENTER_INSTRUCTOR_ROOM, state.stage());
        verify(gateway).stop(entry);
        verify(gateway, never()).navigate(entry, new Point(0, 0), true);
    }

    @Test
    void yieldsToMovementWhileWalkingToTheLithHarborShipExit() {
        Character agent = beginner("ShipArrival", 9, 104000000);
        when(agent.getPosition()).thenReturn(new Point(3_200, -223));
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.portalPosition(agent, 31)).thenReturn(new Point(3_800, -223));

        assertFalse(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).navigate(entry, new Point(3_800, -223), true);
    }

    @Test
    void yieldsToMovementWhenOlafIsNotYetInInteractionRange() {
        Character agent = beginner("WalkToOlaf", 9, 104000000);
        when(agent.getPosition()).thenReturn(new Point(2_894, 423));
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point olaf = new Point(1_000, 0);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, 1002101)).thenReturn(olaf);

        assertFalse(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).navigate(entry, olaf, true);
    }

    @Test
    void completesSeededBiggsQuestAtOlafBeforeAnyTaxiTravel() {
        Character agent = beginner("BiggsReady", 9, 104000000);
        when(agent.getPosition()).thenReturn(new Point(3_392, 518));
        when(agent.getQuestStatus(1046)).thenReturn((byte) 1);
        AgentRuntimeEntry entry = entry(agent, "warrior-standard-v1",
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1002101);
        when(gateway.npcPosition(agent, 1002101)).thenReturn(new Point(3_392, 518));
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
    void resetFixtureStartsInstructorTrainingBeforePlannedShop() {
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
        assertEquals(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, state.stage());
    }

    @Test
    void completedMilestoneFinishesAtNearestTownInsteadOfReturningToInstructor() {
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

        assertFalse(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, mock(PrimitiveCapabilityGateway.class)));
        assertEquals(AgentCareerProgressionState.Stage.COMPLETE, state.stage());
    }

    @Test
    void completedInstructorChainSchedulesThePlannedShopBeforeCatchUpQuests() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(44);
        when(agent.getName()).thenReturn("HomePackNext");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(15);
        when(agent.getMapId()).thenReturn(103000003);
        AgentRuntimeEntry entry = entry(agent, "thief-claw-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        for (int questId : Set.of(2140, 2141, 2142, 2143)) {
            when(gateway.questStatus(agent, questId)).thenReturn(2);
        }

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        assertEquals(4, state.trainingQuestIndex());
        assertEquals(AgentCareerProgressionState.Stage.TRAVEL_TO_INITIAL_SHOP, state.stage());
    }

    @Test
    void returningFromPostTrainingShopHandsOffToCareerHomeQuestPack() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(47);
        when(agent.getName()).thenReturn("ShoppingDone");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(14);
        when(agent.getMapId()).thenReturn(103000003);
        AgentRuntimeEntry entry = entry(agent, "thief-claw-standard-v1",
                AgentCareerProgressionState.Stage.RETURN_TO_INSTRUCTOR);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        state.trainingQuestIndex(4);

        assertTrue(AgentFirstJobJourneyRuntime.tick(
                entry, agent, 100L, mock(PrimitiveCapabilityGateway.class)));

        assertEquals(AgentCareerProgressionState.Stage.HOME_QUEST_PACK, state.stage());
    }

    @Test
    void activeThiefTrainingQuestEntersTrainingCenterThroughPowerBFore() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        when(agent.getName()).thenReturn("TrainingDoor");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getMapId()).thenReturn(103010000);
        when(agent.getPosition()).thenReturn(new Point(1_051, 124));
        AgentRuntimeEntry entry = entry(agent, "thief-dagger-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1052114);
        when(gateway.questStatus(agent, 2140)).thenReturn(1);
        when(gateway.npcPosition(agent, 1052114)).thenReturn(new Point(1_051, 124));
        when(gateway.runNpcScript(agent, 1052114, 1)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).runNpcScript(agent, 1052114, 1);
        verify(gateway, never()).grind(entry, Set.of(130100));
    }

    @Test
    void activePirateTrainingQuestUsesUnreachableEntranceNpcFromLowerPlatform() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(50);
        when(agent.getName()).thenReturn("PirateTrainingDoor");
        when(agent.getJob()).thenReturn(Job.PIRATE);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getMapId()).thenReturn(120010000);
        when(agent.getPosition()).thenReturn(new Point(516, 122));
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = entry(agent, "pirate-gun-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1095002);
        when(gateway.questStatus(agent, 2193)).thenReturn(1);
        Point npcPosition = new Point(133, -86);
        when(gateway.npcPosition(agent, 1095002)).thenReturn(npcPosition);
        when(gateway.runNpcScript(agent, 1095002, 0)).thenReturn(true);
        AgentNavigationGraph graph = mock(AgentNavigationGraph.class);
        when(graph.findRegionId(map, agent.getPosition())).thenReturn(8);
        when(graph.findRegionId(map, npcPosition)).thenReturn(-1);

        try (MockedStatic<AgentNavigationGraphService> graphs =
                     mockStatic(AgentNavigationGraphService.class)) {
            graphs.when(() -> AgentNavigationGraphService.peekBestGraph(
                    org.mockito.ArgumentMatchers.eq(map), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(graph);

            assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));
        }

        verify(gateway).runNpcScript(agent, 1095002, 0);
        verify(gateway, never()).navigate(entry, npcPosition, true);
    }

    @Test
    void activeThiefOctopusQuestGrindsInsideAnyTrainingCenterInstance() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(48);
        when(agent.getName()).thenReturn("TrainingOctopus");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(13);
        when(agent.getMapId()).thenReturn(910310004);
        AgentRuntimeEntry entry = entry(agent, "thief-claw-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        state.trainingQuestIndex(3);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, 2143)).thenReturn(1);
        when(gateway.liveMonsterCount(agent, Set.of(1120100))).thenReturn(1);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).grind(entry, Set.of(1120100));
    }

    @Test
    void completedInstructorKillCounterReturnsAndCompletesInsteadOfGrindingForever() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(49);
        when(agent.getName()).thenReturn("TrainingComplete");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(12);
        when(agent.getMapId()).thenReturn(103000003);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = entry(agent, "thief-dagger-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1052001);
        when(gateway.questStatus(agent, 2140)).thenReturn(1);
        when(gateway.questProgress(agent, 2140, 130100)).thenReturn(20);
        when(gateway.completeQuest(agent, 2140, 1052001)).thenReturn(false);
        when(gateway.forceCompleteQuest(agent, 2140, 1052001)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway, never()).grind(entry, Set.of(130100));
        verify(gateway).completeQuest(agent, 2140, 1052001);
        verify(gateway).forceCompleteQuest(agent, 2140, 1052001);
        assertEquals(1, state.trainingQuestIndex());
    }

    @Test
    void completedThiefCounterLeavesTheTrainingInstanceBeforeReturningToInstructor() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(51);
        when(agent.getName()).thenReturn("TrainingExit");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(12);
        when(agent.getMapId()).thenReturn(910310004);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = entry(agent, "thief-dagger-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point exit = new Point(100, 0);
        when(gateway.questStatus(agent, 2140)).thenReturn(1);
        when(gateway.questProgress(agent, 2140, 130100)).thenReturn(20);
        when(gateway.directPortalIdTo(agent, 103010000)).thenReturn(1);
        when(gateway.portalPosition(agent, 1)).thenReturn(exit);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).navigate(entry, exit, true);
        verify(gateway, never()).grind(entry, Set.of(130100));
    }

    @Test
    void incompleteInstructorKillCounterContinuesHunting() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(50);
        when(agent.getName()).thenReturn("TrainingActive");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(10);
        when(agent.getMapId()).thenReturn(910310004);
        AgentRuntimeEntry entry = entry(agent, "thief-dagger-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, 2140)).thenReturn(1);
        when(gateway.questProgress(agent, 2140, 130100)).thenReturn(19);
        when(gateway.liveMonsterCount(agent, Set.of(130100))).thenReturn(1);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).grind(entry, Set.of(130100));
        verify(gateway, never()).completeQuest(agent, 2140, 1052001);
    }

    @Test
    void exhaustedInstructorInstanceLeavesAndReentersInsteadOfIdling() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(52);
        when(agent.getName()).thenReturn("TrainingExhausted");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(12);
        when(agent.getMapId()).thenReturn(910310004);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = entry(agent, "thief-dagger-standard-v1",
                AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point exit = new Point(100, 0);
        when(gateway.questStatus(agent, 2140)).thenReturn(1);
        when(gateway.questProgress(agent, 2140, 130100)).thenReturn(6);
        when(gateway.liveMonsterCount(agent, Set.of(130100))).thenReturn(0);
        when(gateway.directPortalIdTo(agent, 103010000)).thenReturn(1);
        when(gateway.portalPosition(agent, 1)).thenReturn(exit);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));
        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 16_000L, gateway));

        verify(gateway).stop(entry);
        verify(gateway).navigate(entry, exit, true);
        verify(gateway).grind(entry, Set.of(130100));
    }

    @Test
    void reachingLevel15DoesNotSkipAnInProgressRequiredHomePack() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(45);
        when(agent.getName()).thenReturn("StillQuesting");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(15);
        when(agent.getMapId()).thenReturn(103000000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = entry(agent, "thief-claw-standard-v1",
                AgentCareerProgressionState.Stage.HOME_QUEST_PACK);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("kerning-pre15");
        int nellaStepIndex = java.util.stream.IntStream.range(0, pack.steps().size())
                .filter(index -> pack.steps().get(index).questId() == 28270
                        && !pack.steps().get(index).complete())
                .findFirst()
                .orElseThrow();
        state.questPackIndex(nellaStepIndex);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1052103);
        when(gateway.questStatus(agent, 28270)).thenReturn(0);
        when(gateway.canStartQuest(agent, 28270, 1052103)).thenReturn(true);
        when(gateway.startQuest(agent, 28270, 1052103)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).startQuest(agent, 28270, 1052103);
        assertEquals(AgentCareerProgressionState.Stage.HOME_QUEST_PACK, state.stage());
    }

    @Test
    void homePackReconcilesCompletedQuestAndResumesAtNextInteraction() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(46);
        when(agent.getName()).thenReturn("PackResume");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(14);
        when(agent.getMapId()).thenReturn(103000000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = entry(agent, "thief-claw-standard-v1",
                AgentCareerProgressionState.Stage.HOME_QUEST_PACK);
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        state.questPackIndex(3);
        PrimitiveCapabilityGateway gateway = npcGateway(agent, 1052106);
        when(gateway.questStatus(agent, 2090)).thenReturn(0);
        when(gateway.canStartQuest(agent, 2090, 1052106)).thenReturn(true);
        when(gateway.startQuest(agent, 2090, 1052106)).thenReturn(true);

        assertTrue(AgentFirstJobJourneyRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).startQuest(agent, 2090, 1052106);
        assertEquals(4, state.questPackIndex());
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
