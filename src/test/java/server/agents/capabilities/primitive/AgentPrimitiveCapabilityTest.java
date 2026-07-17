package server.agents.capabilities.primitive;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.capabilities.navigation.AgentMapleIslandTravelRuntime;
import server.agents.capabilities.navigation.AgentMapleIslandTravelSettings;
import server.agents.plans.mapleisland.AgentSplitRoadRouteService;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.reactor.AgentReactorScopePolicy;
import server.agents.capabilities.reactor.AgentReactorTargetSelector;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityMemory;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.integration.AgentCharacterStateSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Reactor;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPrimitiveCapabilityTest {
    @Test
    void navigationDelegatesToExistingMovementAndVerifiesArrival() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0), new Point(95, 0));
        AgentNavigationCapability capability = new AgentNavigationCapability(fixture.gateway);
        AgentNavigationCapability.Command command = new AgentNavigationCapability.Command(
                10000, new Point(100, 0), 5, true);

        AgentCapabilityStep running = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.RUNNING, running.status());
        assertFalse(running.consumedTick());
        verify(fixture.gateway).navigate(fixture.entry, new Point(100, 0), true);

        AgentCapabilityStep success = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.SUCCESS, success.status());
        verify(fixture.gateway).stop(fixture.entry);
    }

    @Test
    void navigationWaitsForAirborneAgentToLandBeforeCompleting() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(100, 0));
        when(fixture.gateway.grounded(fixture.agent)).thenReturn(false, true);
        AgentNavigationCapability capability = new AgentNavigationCapability(fixture.gateway);
        var command = new AgentNavigationCapability.Command(10000, new Point(100, 0), 5, true);

        AgentCapabilityStep airborne = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.RUNNING, airborne.status());
        assertFalse(airborne.consumedTick());

        assertEquals(AgentCapabilityStatus.SUCCESS, capability.tick(fixture.context(), command).status());
    }

    @Test
    void navigationCanCompleteAtExplicitNpcClimbingAnchor() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(100, 80));
        when(fixture.gateway.grounded(fixture.agent)).thenReturn(false);
        AgentClimbStateRuntime.setClimbingOnRope(
                fixture.entry, new server.maps.Rope(90, 20, 200, false));
        AgentNavigationCapability capability = new AgentNavigationCapability(fixture.gateway);
        var command = new AgentNavigationCapability.Command(
                10000, new Point(100, 80), 5, true, true);

        assertEquals(AgentCapabilityStatus.RUNNING, capability.tick(fixture.context(), command).status());
        AgentClimbStateRuntime.setClimbingOnRope(
                fixture.entry, new server.maps.Rope(100, 20, 200, false));
        assertEquals(AgentCapabilityStatus.SUCCESS, capability.tick(fixture.context(), command).status());
        verify(fixture.gateway).stop(fixture.entry);
    }

    @Test
    void portalScopeBlocksTrainingCenterBeforeEntryAttempt() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1000000);
        AgentPortalTravelCapability capability = new AgentPortalTravelCapability(
                fixture.gateway, new AmherstScopePolicy());

        AgentCapabilityStep result = capability.tick(fixture.context(),
                new AgentPortalTravelCapability.Command(1000000, 1, 1010000, true));

        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP, result.status());
        verify(fixture.gateway, never()).enterPortal(any(), eq(1));
    }

    @Test
    void portalUsesLivePresenceThenSettlesAtGroundedDestination() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000, 20000, 20000, 20000);
        when(fixture.gateway.portalPresent(fixture.agent, 1)).thenReturn(true);
        when(fixture.gateway.portalPosition(fixture.agent, 1)).thenReturn(new Point(50, 0));
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        when(fixture.gateway.enterPortal(fixture.agent, 1)).thenReturn(true);
        AgentPortalTravelCapability capability = new AgentPortalTravelCapability(
                fixture.gateway, new AmherstScopePolicy());
        var command = new AgentPortalTravelCapability.Command(10000, 1, 20000, true);
        AgentCapabilityMemory memory = new AgentCapabilityMemory();

        assertEquals(AgentCapabilityStatus.RUNNING,
                capability.tick(fixture.context(100L, memory), command).status());
        assertEquals(AgentCapabilityStatus.RUNNING,
                capability.tick(fixture.context(200L, memory), command).status());
        assertEquals(AgentCapabilityStatus.RUNNING,
                capability.tick(fixture.context(2_699L, memory), command).status());
        assertEquals(AgentCapabilityStatus.SUCCESS,
                capability.tick(fixture.context(2_700L, memory), command).status());
        verify(fixture.gateway).stop(fixture.entry);
    }

    @Test
    void portalDestinationLandingDoesNotConsumeMovementTick() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(20000);
        when(fixture.gateway.grounded(fixture.agent)).thenReturn(false);
        AgentPortalTravelCapability capability = new AgentPortalTravelCapability(
                fixture.gateway, new AmherstScopePolicy());

        AgentCapabilityStep result = capability.tick(fixture.context(),
                new AgentPortalTravelCapability.Command(10000, 1, 20000, true));

        assertEquals(AgentCapabilityStatus.RUNNING, result.status());
        assertFalse(result.consumedTick());
        verify(fixture.gateway, never()).stop(fixture.entry);
    }

    @Test
    void portalOutsideApproachRangeHandsOffToNavigationBeforeEntry() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.portalPresent(fixture.agent, 1)).thenReturn(true);
        when(fixture.gateway.portalPosition(fixture.agent, 1)).thenReturn(new Point(500, 0));
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        AgentPortalTravelCapability capability = new AgentPortalTravelCapability(
                fixture.gateway, new AmherstScopePolicy());

        AgentCapabilityStep result = capability.tick(fixture.context(),
                new AgentPortalTravelCapability.Command(10000, 1, 20000, true));

        assertEquals(AgentCapabilityStatus.WAITING_CHILD, result.status());
        assertEquals("navigation", result.child().capabilityId());
        verify(fixture.gateway, never()).enterPortal(any(), eq(1));
    }

    @Test
    void splitRoadUpperVariantUsesInternalPortalBeforeSouthperryPortal() {
        Fixture fixture = fixture();
        long seed = 0L;
        do {
            AgentMapleIslandTravelRuntime.configure(fixture.entry, new AgentMapleIslandTravelSettings(
                    seed++, true, 1.15d, false, 0.0d, 3_000L, 0L));
        } while (!AgentSplitRoadRouteService.INSTANCE.select(fixture.entry).usesInternalPortal());
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1020000);
        when(fixture.gateway.portalPresent(fixture.agent, 1)).thenReturn(true);
        when(fixture.gateway.portalPresent(
                fixture.agent, AgentSplitRoadRouteService.UPPER_PLATFORM_PORTAL_ID)).thenReturn(true);
        when(fixture.gateway.portalPosition(fixture.agent, 1)).thenReturn(new Point(701, 209));
        when(fixture.gateway.portalPosition(
                fixture.agent, AgentSplitRoadRouteService.UPPER_PLATFORM_PORTAL_ID))
                .thenReturn(new Point(174, 217));
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(174, 217));
        when(fixture.gateway.enterPortal(
                fixture.agent, AgentSplitRoadRouteService.UPPER_PLATFORM_PORTAL_ID)).thenReturn(true);
        AgentPortalTravelCapability capability = new AgentPortalTravelCapability(
                fixture.gateway, new AmherstScopePolicy());

        AgentCapabilityStep result = capability.tick(fixture.context(),
                new AgentPortalTravelCapability.Command(1020000, 1, 2000000, false,
                        AgentSplitRoadRouteService.INSTANCE.plan(fixture.entry, 1020000, 2000000)));

        assertEquals(AgentCapabilityStatus.RUNNING, result.status());
        verify(fixture.gateway).enterPortal(
                fixture.agent, AgentSplitRoadRouteService.UPPER_PLATFORM_PORTAL_ID);
        verify(fixture.gateway, never()).enterPortal(fixture.agent, 1);
    }

    @Test
    void combatDelegatesToGrindWithOnlyRequiredMobIdsAndStopsAtLiveProgress() {
        Fixture fixture = fixture();
        when(fixture.gateway.alive(fixture.agent)).thenReturn(true);
        when(fixture.gateway.questProgress(fixture.agent, 1037, 100100)).thenReturn(4, 10);
        when(fixture.gateway.liveMonsterCount(fixture.agent, Set.of(100100))).thenReturn(2);
        AgentCombatCapability capability = new AgentCombatCapability(fixture.gateway);
        AgentCombatCapability.Command command = new AgentCombatCapability.Command(
                1037, Map.of(100100, 10));

        AgentCapabilityStep running = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.RUNNING, running.status());
        assertFalse(running.consumedTick());
        verify(fixture.gateway).grind(fixture.entry, Set.of(100100));

        AgentCapabilityStep success = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.SUCCESS, success.status());
        verify(fixture.gateway).stop(fixture.entry);
    }

    @Test
    void inventoryAndItemUseVerifyLiveCounts() {
        Fixture fixture = fixture();
        when(fixture.gateway.itemCount(fixture.agent, 2010007)).thenReturn(1, 1, 0);
        when(fixture.gateway.freeSlots(fixture.agent, 2010007)).thenReturn(3);
        when(fixture.gateway.useItem(fixture.agent, 2010007)).thenReturn(true);
        when(fixture.gateway.questStatus(fixture.agent, 1021)).thenReturn(1);

        AgentCapabilityStep inspected = new AgentInventoryInspectionCapability(fixture.gateway).tick(
                fixture.context(), new AgentInventoryInspectionCapability.Command(2010007, 1, 1));
        assertEquals(AgentCapabilityStatus.SUCCESS, inspected.status());
        assertEquals(new AgentInventoryInspectionCapability.Snapshot(2010007, 1, 3),
                inspected.result().output());

        AgentItemUseCapability use = new AgentItemUseCapability(fixture.gateway);
        AgentItemUseCapability.Command command = new AgentItemUseCapability.Command(2010007, 1, 0, 1021, 1);
        assertEquals(AgentCapabilityStatus.RUNNING, use.tick(fixture.context(), command).status());
        assertEquals(AgentCapabilityStatus.SUCCESS, use.tick(fixture.context(), command).status());
    }

    @Test
    void questPrimitivesUseNormalGatewayThenVerifyStatus() {
        Fixture fixture = fixture();
        when(fixture.gateway.questStatus(fixture.agent, 1031)).thenReturn(0, 1, 1, 2);
        when(fixture.gateway.canStartQuest(fixture.agent, 1031, 2101)).thenReturn(true);
        when(fixture.gateway.canCompleteQuest(fixture.agent, 1031, 2100)).thenReturn(true);
        when(fixture.gateway.startQuest(fixture.agent, 1031, 2101)).thenReturn(true);
        when(fixture.gateway.completeQuest(fixture.agent, 1031, 2100)).thenReturn(true);
        AmherstScopePolicy scope = new AmherstScopePolicy();

        AgentQuestStartPrimitiveCapability start = new AgentQuestStartPrimitiveCapability(fixture.gateway, scope);
        var startCommand = new AgentQuestStartPrimitiveCapability.Command(1031, 2101, true);
        assertEquals(AgentCapabilityStatus.RUNNING, start.tick(fixture.context(), startCommand).status());
        assertEquals(AgentCapabilityStatus.SUCCESS, start.tick(fixture.context(), startCommand).status());

        AgentQuestCompletePrimitiveCapability complete =
                new AgentQuestCompletePrimitiveCapability(fixture.gateway, scope);
        var completeCommand = new AgentQuestCompletePrimitiveCapability.Command(1031, 2100, true);
        assertEquals(AgentCapabilityStatus.RUNNING, complete.tick(fixture.context(), completeCommand).status());
        assertEquals(AgentCapabilityStatus.SUCCESS, complete.tick(fixture.context(), completeCommand).status());
    }

    @Test
    void questStartAcceptsObservedAutoCompletion() {
        Fixture fixture = fixture();
        when(fixture.gateway.questStatus(fixture.agent, 1030)).thenReturn(2);

        AgentCapabilityStep result = new AgentQuestStartPrimitiveCapability(
                fixture.gateway, new AmherstScopePolicy()).tick(
                fixture.context(), new AgentQuestStartPrimitiveCapability.Command(1030, 2103, true));

        assertEquals(AgentCapabilityStatus.SUCCESS, result.status());
        verify(fixture.gateway, never()).startQuest(any(), eq(1030), eq(2103));
    }

    @Test
    void npcRequiresLivePlacementAndRange() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.npcPosition(fixture.agent, 2101)).thenReturn(new Point(20, 0));
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        when(fixture.gateway.interactNpc(fixture.agent, 2101, AgentNpcInteractionType.TALK, null))
                .thenReturn(true);
        AgentNpcInteractionPrimitiveCapability capability = new AgentNpcInteractionPrimitiveCapability(
                fixture.gateway, new AmherstScopePolicy());

        AgentCapabilityStep result = capability.tick(fixture.context(),
                new AgentNpcInteractionPrimitiveCapability.Command(
                        10000, 2101, AgentNpcInteractionType.TALK, null, 50, false));

        assertEquals(AgentCapabilityStatus.SUCCESS, result.status());
        verify(fixture.gateway).facePosition(fixture.agent, new Point(20, 0));
    }

    @Test
    void npcQuestInteractionVerifiesActionSpecificLiveState() {
        Fixture fixture = fixture();
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(10000);
        when(fixture.gateway.npcPosition(fixture.agent, 2101)).thenReturn(new Point(20, 0));
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        when(fixture.gateway.interactNpc(
                fixture.agent, 2101, AgentNpcInteractionType.QUEST_START, 1031)).thenReturn(true);
        when(fixture.gateway.questStatus(fixture.agent, 1031)).thenReturn(1);

        AgentCapabilityStep result = new AgentNpcInteractionPrimitiveCapability(
                fixture.gateway, new AmherstScopePolicy()).tick(
                fixture.context(),
                new AgentNpcInteractionPrimitiveCapability.Command(
                        10000, 2101, AgentNpcInteractionType.QUEST_START, 1031, 50, true));

        assertEquals(AgentCapabilityStatus.SUCCESS, result.status());
    }

    @Test
    void lootDelegatesPickupUntilRequiredInventoryStateExists() {
        Fixture fixture = fixture();
        when(fixture.gateway.itemCount(fixture.agent, 4031161)).thenReturn(0, 1);
        when(fixture.gateway.freeSlots(fixture.agent, 4031161)).thenReturn(1);
        AgentLootCapability capability = new AgentLootCapability(fixture.gateway);
        AgentLootCapability.Command command = new AgentLootCapability.Command(Map.of(4031161, 1));

        AgentCapabilityStep running = capability.tick(fixture.context(), command);
        assertEquals(AgentCapabilityStatus.RUNNING, running.status());
        assertFalse(running.consumedTick());
        verify(fixture.gateway).lootNearby(fixture.agent, Set.of(4031161));
        assertEquals(AgentCapabilityStatus.SUCCESS, capability.tick(fixture.context(), command).status());
    }

    @Test
    void reactorUsesSelectedLiveTargetAndVerifiesResultingItems() {
        Fixture fixture = fixture();
        Reactor reactor = mock(Reactor.class);
        when(reactor.isAlive()).thenReturn(true);
        when(reactor.isActive()).thenReturn(true);
        when(reactor.getObjectId()).thenReturn(77);
        when(reactor.getId()).thenReturn(2000);
        when(reactor.getName()).thenReturn("box");
        when(reactor.getPosition()).thenReturn(new Point(10, 0));
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1000000);
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        when(fixture.gateway.reactors(fixture.agent)).thenReturn(List.of(reactor));
        when(fixture.gateway.itemCount(fixture.agent, 4031161)).thenReturn(0, 1);
        when(fixture.gateway.hitReactor(fixture.agent, 77)).thenReturn(true);
        AgentReactorPrimitiveCapability capability = new AgentReactorPrimitiveCapability(
                fixture.gateway, new AgentReactorScopePolicy(), new AgentReactorTargetSelector());
        var command = new AgentReactorPrimitiveCapability.Command(
                1000000, 1008, 2000, "box", 50, Map.of(4031161, 1));

        assertEquals(AgentCapabilityStatus.RUNNING, capability.tick(fixture.context(), command).status());
        assertEquals(AgentCapabilityStatus.SUCCESS, capability.tick(fixture.context(), command).status());
    }

    @Test
    void reactorCollectsNormalMapDropBeforeVerifyingInventory() {
        Fixture fixture = fixture();
        Reactor reactor = mock(Reactor.class);
        when(reactor.isAlive()).thenReturn(true);
        when(reactor.isActive()).thenReturn(true);
        when(reactor.getObjectId()).thenReturn(77);
        when(reactor.getId()).thenReturn(2000);
        when(reactor.getName()).thenReturn("box");
        when(reactor.getPosition()).thenReturn(new Point(10, 0));
        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1000000);
        when(fixture.gateway.position(fixture.agent)).thenReturn(new Point(0, 0));
        when(fixture.gateway.reactors(fixture.agent)).thenReturn(List.of(reactor), List.of(), List.of());
        when(fixture.gateway.itemCount(fixture.agent, 4031161)).thenReturn(0, 0, 1);
        when(fixture.gateway.hitReactor(fixture.agent, 77)).thenReturn(true);
        when(fixture.gateway.lootNearby(fixture.agent, Set.of(4031161))).thenReturn(true);
        AgentReactorPrimitiveCapability capability = new AgentReactorPrimitiveCapability(
                fixture.gateway, new AgentReactorScopePolicy(), new AgentReactorTargetSelector());
        var command = new AgentReactorPrimitiveCapability.Command(
                1000000, 1008, 2000, "box", 50, Map.of(4031161, 1));

        assertEquals(AgentCapabilityStatus.RUNNING, capability.tick(fixture.context(), command).status());
        assertEquals(AgentCapabilityStatus.RUNNING, capability.tick(fixture.context(), command).status());
        assertEquals(AgentCapabilityStatus.SUCCESS, capability.tick(fixture.context(), command).status());
    }

    @Test
    void recoveryDelegatesAndFinalStateReadsLiveValues() {
        Fixture fixture = fixture();
        when(fixture.gateway.alive(fixture.agent)).thenReturn(false, true, true);
        when(fixture.gateway.stuckDurationMs(fixture.entry)).thenReturn(0);
        AgentRecoveryCapability recovery = new AgentRecoveryCapability(fixture.gateway);
        AgentCapabilityStep recovering = recovery.tick(fixture.context(), new AgentRecoveryCapability.Command(true));
        assertEquals(AgentCapabilityStatus.RUNNING, recovering.status());
        assertFalse(recovering.consumedTick());
        assertEquals(AgentCapabilityStatus.SUCCESS,
                recovery.tick(fixture.context(), new AgentRecoveryCapability.Command(true)).status());

        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1000000);
        when(fixture.gateway.questStatus(fixture.agent, 1031)).thenReturn(2);
        when(fixture.gateway.itemCount(fixture.agent, 4031161)).thenReturn(1);
        when(fixture.gateway.characterState(fixture.agent)).thenReturn(
                new AgentCharacterStateSnapshot(0, 1, 50, 50, 5, 5, true));
        AgentFinalStateVerificationCapability finalState = new AgentFinalStateVerificationCapability(fixture.gateway);
        assertEquals(AgentCapabilityStatus.SUCCESS, finalState.tick(fixture.context(),
                new AgentFinalStateVerificationCapability.Command(
                        1000000, Map.of(1031, 2), Map.of(4031161, 1), true)).status());
    }

    @Test
    void questItemLootPolicyAndForbiddenFinalQuestReturnStructuredBlockers() {
        Fixture fixture = fixture();
        when(fixture.gateway.questItem(2000000)).thenReturn(false);
        AgentCapabilityStep loot = new AgentLootCapability(fixture.gateway).tick(
                fixture.context(),
                new AgentLootCapability.Command(
                        Map.of(2000000, 1), AgentLootCapability.ProtectionPolicy.QUEST_ITEMS_ONLY));
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, loot.status());

        when(fixture.gateway.mapId(fixture.agent)).thenReturn(1000000);
        when(fixture.gateway.alive(fixture.agent)).thenReturn(true);
        when(fixture.gateway.characterState(fixture.agent)).thenReturn(
                new AgentCharacterStateSnapshot(0, 1, 50, 50, 5, 5, true));
        when(fixture.gateway.questStatus(fixture.agent, 1018)).thenReturn(2);
        AgentCapabilityStep finalState = new AgentFinalStateVerificationCapability(fixture.gateway).tick(
                fixture.context(),
                new AgentFinalStateVerificationCapability.Command(
                        1000000, Map.of(), Map.of(), true, 0, 1, Set.of(1018)));
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST, finalState.status());
    }

    @Test
    void malformedCommandsAreRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentPortalTravelCapability.Command(10000, -1, 20000, true));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentQuestStateCapability.Command(1000, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentReactorPrimitiveCapability.Command(
                        1000000, 1008, 2000, null, 50, Map.of()));
    }

    private static Fixture fixture() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        return new Fixture(agent, entry, gateway);
    }

    private record Fixture(Character agent,
                           AgentRuntimeEntry entry,
                           PrimitiveCapabilityGateway gateway) {
        AgentCapabilityContext context() {
            return new AgentCapabilityContext(entry, agent, 100L, 0L, 0, null);
        }

        AgentCapabilityContext context(long nowMs, AgentCapabilityMemory memory) {
            return new AgentCapabilityContext(entry, agent, nowMs, 0L, 0, null, memory);
        }
    }
}
