package server.agents.capabilities.objective;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.movement.AgentRelaxerSpotCatalog;
import server.agents.capabilities.movement.AgentRelaxerSpotReservationRuntime;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityMemory;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.atLeastOnce;

class AmherstObjectiveCapabilitiesTest {
    @AfterEach
    void releaseRelaxerSpot() {
        AgentRelaxerSpotReservationRuntime.release(77);
    }

    @Test
    void combatChildAllowsTimeForNormalTenMobHunt() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var support = new AmherstObjectiveCapabilitySupport(fixture.gateway);

        AgentCapabilityInvocation<?> invocation = support.combat(1037, Map.of(100100, 10));

        assertEquals(180_000L, invocation.timeoutMs());
    }

    @Test
    void npcInteractionFacesTargetAndHonorsConfiguredPause() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var capability = new NpcQuestObjectiveCapability(fixture.gateway, () -> 500L);
        var command = new NpcQuestObjectiveCapability.Command("q1031-start", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 1)), false);
        AgentCapabilityMemory memory = new AgentCapabilityMemory();

        AgentCapabilityStep waiting = capability.tick(new AgentCapabilityContext(
                fixture.entry, fixture.agent, 100L, 0L, 0, null, memory), command);
        assertEquals(AgentCapabilityStatus.RUNNING, waiting.status());
        verify(fixture.gateway).facePosition(fixture.agent, new Point(20, 0));

        AgentCapabilityStep stillWaiting = capability.tick(new AgentCapabilityContext(
                fixture.entry, fixture.agent, 599L, 499L, 0, null, memory), command);
        assertEquals(AgentCapabilityStatus.RUNNING, stillWaiting.status());

        AgentCapabilityStep ready = capability.tick(new AgentCapabilityContext(
                fixture.entry, fixture.agent, 600L, 500L, 0, null, memory), command);
        assertEquals(AgentCapabilityStatus.WAITING_CHILD, ready.status());
        assertEquals("npc-interaction", ready.child().capabilityId());
    }

    @Test
    void npcQuest1031SuspendsForChildrenAndCompletesNormally() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var command = new NpcQuestObjectiveCapability.Command("q1031", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 2)), false);

        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);
        assertTrue(AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L));
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 40);

        assertEquals(2, fixture.quests.get(1031));
        assertSuccess(fixture);
    }

    @Test
    void forceCompleteQuestApproachesYoonaAndBypassesMissingShoppingGuide() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1010000);
        var command = new ForceCompleteQuestObjectiveCapability.Command(
                "q8020-force-complete", 1010000, 8020, 20100);

        assign(fixture, new ForceCompleteQuestObjectiveCapability(
                        fixture.gateway, AmherstScopePolicy.southperry(), AmherstNpcInteractionDelay.NONE, () -> 2L),
                command, 10_000L);
        run(fixture, 1L, 30, 1L);

        assertEquals(2, fixture.quests.get(8020));
        verify(fixture.gateway).beginFieldAbsence(fixture.agent, 2_002L);
        verify(fixture.gateway).endFieldAbsence(fixture.agent);
        verify(fixture.gateway).forceCompleteQuest(fixture.agent, 8020, 20100);
        assertTrue(AgentNpcInteractionAnchorCatalog.anchors(1010000, 20100)
                .contains(fixture.position));
        verify(fixture.gateway, atLeastOnce()).facePosition(fixture.agent, new Point(-188, 85));
        assertSuccess(fixture);
    }

    @Test
    void forceCompleteYoonaQuizThreeDoesNotSimulateAnotherCashShopVisit() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1010000);
        var command = new ForceCompleteQuestObjectiveCapability.Command(
                "q8023-force-complete", 1010000, 8023, 20100);

        assign(fixture, new ForceCompleteQuestObjectiveCapability(
                        fixture.gateway, AmherstScopePolicy.southperry(), AmherstNpcInteractionDelay.NONE, () -> 2L),
                command, 10_000L);
        run(fixture, 1L, 30, 1L);

        assertEquals(2, fixture.quests.get(8023));
        verify(fixture.gateway, never()).beginFieldAbsence(any(), anyLong());
        verify(fixture.gateway, never()).endFieldAbsence(any());
        verify(fixture.gateway).forceCompleteQuest(fixture.agent, 8023, 20100);
        assertSuccess(fixture);
    }

    @Test
    void inventoryUse1021InspectsUsesAndVerifies() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.quests.put(1021, 1);
        fixture.items.put(2010007, 1);

        assign(fixture, new InventoryUseObjectiveCapability(fixture.gateway),
                new InventoryUseObjectiveCapability.Command("q1021-use", 1021, 2010007), 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 30);

        assertEquals(0, fixture.items.get(2010007));
        assertSuccess(fixture);
    }

    @Test
    void combat1037DelegatesKillsAndVerifiesProgress() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.quests.put(1037, 1);
        fixture.combatQuestId = 1037;

        assign(fixture, new CombatQuestObjectiveCapability(fixture.gateway),
                new CombatQuestObjectiveCapability.Command("q1037-kills", 50000, 1037,
                        Map.of(100100, 10)), 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 40);

        assertTrue(fixture.progress.get(MutablePrimitiveGatewayFixture.key(1037, 100100)) >= 10);
        assertSuccess(fixture);
    }

    @Test
    void combat1035KillsTutorialSentinelAndCollectsShellpiece() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(40000);
        fixture.quests.put(1035, 1);
        fixture.combatQuestId = 1035;
        fixture.lootRewards.put(4031802, 1);

        assign(fixture, new CombatQuestObjectiveCapability(fixture.gateway),
                new CombatQuestObjectiveCapability.Command("q1035-kill-loot", 40000, 1035,
                        Map.of(9300018, 1), Map.of(4031802, 1)), 10_000L);
        run(fixture, 1L, 60);

        assertTrue(fixture.progress.get(MutablePrimitiveGatewayFixture.key(1035, 9300018)) >= 1);
        assertEquals(1, fixture.items.get(4031802));
        assertSuccess(fixture);
    }

    @Test
    void npcObjectiveTraversesEachInScopePortalHopBeforeInteraction() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(30001);
        fixture.directPortalEdges.addAll(Set.of(
                "30001:30000", "30000:40000", "40000:50000"));

        var command = new NpcQuestObjectiveCapability.Command("q1037-start", 50000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1037, 2005, 2103, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 300_000L);
        run(fixture, 1L, 120);

        assertEquals(50000, fixture.mapId.get());
        assertEquals(1, fixture.quests.get(1037));
        assertSuccess(fixture);
    }

    @Test
    void questItemDelivery1038UsesNormalCompletionAndChecksConsumption() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1000000);
        fixture.quests.put(1038, 1);
        fixture.items.put(4031800, 1);

        assign(fixture, new QuestItemDeliveryObjectiveCapability(fixture.gateway),
                new QuestItemDeliveryObjectiveCapability.Command(
                        "q1038-delivery", 1000000, 1038, 12000, Map.of(4031800, 1)), 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 50);

        assertEquals(2, fixture.quests.get(1038));
        assertEquals(0, fixture.items.get(4031800));
        assertSuccess(fixture);
    }

    @Test
    void quiz1009RunsDeterministicNormalQuestPath() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1000000);

        assign(fixture, new QuizObjectiveCapability(fixture.gateway),
                new QuizObjectiveCapability.Command("q1009-quiz", 1000000, 1009, 12101), 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 50);

        assertEquals(2, fixture.quests.get(1009));
        assertSuccess(fixture);
    }

    @Test
    void reactor1008NavigatesHitsLootsAndInspects() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1000000);
        fixture.position = new Point(-100, 0);
        fixture.quests.put(1008, 1);
        fixture.reactorHitsRequired = 4;
        fixture.reactorRewards.put(4031161, 1);
        fixture.reactorRewards.put(4031162, 1);

        assign(fixture, new ReactorLootObjectiveCapability(fixture.gateway),
                new ReactorLootObjectiveCapability.Command("q1008-reactor", 1000000, 1008,
                        null, null, Map.of(4031161, 1, 4031162, 1), null), 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 80, 100L);

        assertEquals(4, fixture.reactorHits.get());
        assertEquals(new Point(30, 0), fixture.position);
        assertEquals(1, fixture.items.get(4031161));
        assertEquals(1, fixture.items.get(4031162));
        assertSuccess(fixture);
    }

    @Test
    void planStopVerifiesFinalScope() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1000000);
        fixture.items.put(3010000, 1);

        assign(fixture, new PlanStopObjectiveCapability(fixture.gateway),
                new PlanStopObjectiveCapability.Command(
                        "stop", 1000000, Set.of(1028), "stop in Amherst", 3010000),
                10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);
        assertEquals(2, fixture.entry.capabilityRuntimeState().frameCount());
        run(fixture, 2L, 20, 100L);

        verify(fixture.gateway).sitChair(fixture.agent, 3010000);
        assertSuccess(fixture);
    }

    @Test
    void amherstPlanStopUsesAReservedCatalogSpotBeforeSitting() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1000000);
        fixture.items.put(3010000, 1);

        assign(fixture, new PlanStopObjectiveCapability(fixture.gateway),
                new PlanStopObjectiveCapability.Command(
                        "stop", 1000000, Map.of(), Set.of(1028), "stop in Amherst",
                        3010000, AgentRelaxerSpotCatalog.Pool.AMHERST),
                10_000L);
        run(fixture, 1L, 30, 100L);

        assertTrue(AgentRelaxerSpotCatalog.spots(AgentRelaxerSpotCatalog.Pool.AMHERST).stream()
                .anyMatch(spot -> spot.x() == fixture.position.x && spot.y() == fixture.position.y));
        assertTrue(AgentRelaxerSpotReservationRuntime.reservedSpot(77).isPresent());
        verify(fixture.gateway).sitChair(fixture.agent, 3010000);
        assertSuccess(fixture);
    }

    @Test
    void childRetryExhaustionPropagatesToParent() {
        var fixture = new MutablePrimitiveGatewayFixture();
        when(fixture.gateway.interactNpc(any(), anyInt(), any(), any())).thenReturn(false);
        var command = new NpcQuestObjectiveCapability.Command("retry", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);

        run(fixture, 1L, 30);

        assertEquals(AgentCapabilityReasonCode.RETRIES_EXHAUSTED,
                fixture.entry.capabilityRuntimeState().lastResult().reasonCode());
    }

    @Test
    void cancellationClearsParentAndChildAndLeavesStructuredResult() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var command = new NpcQuestObjectiveCapability.Command("cancel", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 1L);

        AgentCapabilityRuntime.requestCancellation(fixture.entry);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 2L);

        assertFalse(fixture.entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AgentCapabilityStatus.CANCELLED,
                fixture.entry.capabilityRuntimeState().lastResult().status());
    }

    @Test
    void parentDeadlineTimesOutAStalledNavigationChild() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.position = new Point(0, 0);
        when(fixture.gateway.npcPosition(any(), anyInt())).thenReturn(new Point(1000, 0));
        doNothing().when(fixture.gateway).navigate(any(), any(), any(Boolean.class));
        var command = new NpcQuestObjectiveCapability.Command("timeout", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2100, 2100, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 20L);

        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 100L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 110L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 120L);

        assertEquals(AgentCapabilityStatus.TIMED_OUT,
                fixture.entry.capabilityRuntimeState().lastResult().status());
    }

    @Test
    void npcWithinDefaultClickRangeInteractsWithoutNavigation() {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.position = new Point(-93, 450);
        when(fixture.gateway.npcPosition(any(), anyInt())).thenReturn(new Point(130, 293));
        var command = new NpcQuestObjectiveCapability.Command("q1031-start", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);

        run(fixture, 1L, 30);

        assertEquals(1, fixture.quests.get(1031));
        assertSuccess(fixture);
        verify(fixture.gateway, never()).navigate(any(), any(), any(Boolean.class));
    }

    @Test
    void directObjectiveConstructionStillBlocksExcludedQuest() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var command = new NpcQuestObjectiveCapability.Command("excluded", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1028, 2101, 2100, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);

        run(fixture, 1L, 20);

        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                fixture.entry.capabilityRuntimeState().lastResult().status());
    }

    @Test
    void directObjectiveConstructionBlocksTrainingCenterAndOffIslandMaps() {
        for (int mapId : List.of(1010000, 999999999)) {
            var fixture = new MutablePrimitiveGatewayFixture();
            var command = new NpcQuestObjectiveCapability.Command("map-" + mapId, mapId,
                    List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 2101, 2100, 1)), false);
            assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);

            run(fixture, 1L, 10);

            assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                    fixture.entry.capabilityRuntimeState().lastResult().status());
        }
    }

    @Test
    void directObjectiveConstructionBlocksShanksNpc() {
        var fixture = new MutablePrimitiveGatewayFixture();
        var command = new NpcQuestObjectiveCapability.Command("shanks", 10000,
                List.of(new NpcQuestObjectiveCapability.QuestOperation(1031, 22000, 22000, 1)), false);
        assign(fixture, new NpcQuestObjectiveCapability(fixture.gateway), command, 10_000L);

        run(fixture, 1L, 10);

        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                fixture.entry.capabilityRuntimeState().lastResult().status());
    }

    private static <C extends AgentCapabilityCommand> void assign(
            MutablePrimitiveGatewayFixture fixture,
            AgentExecutableCapability<C> capability,
            C command,
            long timeoutMs) {
        assertTrue(AgentCapabilityRuntime.assign(fixture.entry,
                new AgentCapabilityInvocation<>(capability, command, timeoutMs, 1)));
    }

    private static void run(MutablePrimitiveGatewayFixture fixture, long nowMs, int maxTicks) {
        run(fixture, nowMs, maxTicks, 100L);
    }

    private static void run(MutablePrimitiveGatewayFixture fixture,
                            long nowMs,
                            int maxTicks,
                            long tickStepMs) {
        for (int i = 0; i < maxTicks && fixture.entry.capabilityRuntimeState().hasActiveCapability(); i++) {
            AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, nowMs + i * tickStepMs);
        }
        assertFalse(fixture.entry.capabilityRuntimeState().hasActiveCapability(), "capability did not terminate");
    }

    private static void assertSuccess(MutablePrimitiveGatewayFixture fixture) {
        assertEquals(AgentCapabilityStatus.SUCCESS,
                fixture.entry.capabilityRuntimeState().lastResult().status());
    }
}
