package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentCharacterStateSnapshot;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSecondJobAdvancementRuntimeTest {
    @Test
    void magicianInstructorApproachEntersTreeTunnelFromLowerForest() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("fp-wizard");
        entry.capabilityStates().require(AgentSecondJobAdvancementState.STATE_KEY)
                .begin(branch.id(), 1L);

        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(200, 30, 1_000, 1_000, 500, 500, true));
        when(gateway.mapId(agent)).thenReturn(101020000);
        when(gateway.itemCount(agent, branch.letterItemId())).thenReturn(1);
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(agent.getPosition()).thenReturn(new Point(29, 2_092));
        when(gateway.portalPosition(agent, 9)).thenReturn(new Point(29, 2_092));

        AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);

        verify(gateway).enterPortal(agent, 9);
        verify(gateway, never()).travelTo(
                same(entry), same(agent), eq(branch.instructorMapId()), anyLong());
    }

    @Test
    void magicianInstructorApproachExitsTreeTunnelAtTopPortal() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("fp-wizard");
        entry.capabilityStates().require(AgentSecondJobAdvancementState.STATE_KEY)
                .begin(branch.id(), 1L);

        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(200, 30, 1_000, 1_000, 500, 500, true));
        when(gateway.mapId(agent)).thenReturn(101020001);
        when(gateway.itemCount(agent, branch.letterItemId())).thenReturn(1);
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(agent.getPosition()).thenReturn(new Point(279, -2_182));
        when(gateway.portalPosition(agent, 12)).thenReturn(new Point(279, -2_182));

        AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);

        verify(gateway).enterPortal(agent, 12);
    }

    @Test
    void trialUsesPhysicalGrindLootInsteadOfVacuumPickup() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("fighter");
        entry.capabilityStates().require(AgentSecondJobAdvancementState.STATE_KEY)
                .begin(branch.id(), 1L);

        when(agent.getId()).thenReturn(9_101);
        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(100, 30, 1_000, 1_000, 100, 100, true));
        when(gateway.mapId(agent)).thenReturn(branch.trialMapId());
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(gateway.itemCount(agent, branch.collectionItemId())).thenReturn(0);

        try {
            AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);

            verify(gateway).grind(entry, branch.trialMobIds(), Set.of());
            verify(gateway, never()).lootNearby(agent, Set.of(branch.collectionItemId()));
        } finally {
            AgentSecondJobTrialRegistry.release(branch.trialMapId(), agent.getId());
        }
    }

    @Test
    void leaderTravelUsesCurrentTownTaxiBeforePortalRouting() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("assassin");
        entry.capabilityStates().require(AgentSecondJobAdvancementState.STATE_KEY)
                .begin(branch.id(), 1L);

        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(400, 30, 1_000, 1_000, 500, 500, true));
        when(gateway.mapId(agent)).thenReturn(100000000);
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(gateway.npcPosition(agent, 1012000)).thenReturn(new Point(10, 0));
        when(gateway.grounded(agent)).thenReturn(true);
        when(agent.getPosition()).thenReturn(new Point(10, 0));

        AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);

        verify(gateway).runNpcScript(agent, 1012000, 0, 3, 0);
        verify(gateway, never()).travelTo(
                same(entry), same(agent), eq(branch.leaderMapId()), anyLong());
    }

    @Test
    void examinerTransitionStopsCombatOnceWithoutDiscardingApproachRouteEveryTick() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("hunter");
        state.begin(branch.id(), 1L);

        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(300, 30, 1_000, 1_000, 500, 500, true));
        when(gateway.mapId(agent)).thenReturn(branch.trialMapId());
        when(gateway.itemCount(agent, branch.collectionItemId())).thenReturn(branch.requiredCount());
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, branch.examinerNpcId())).thenReturn(new Point(1_000, 0));
        when(agent.getPosition()).thenReturn(new Point(0, 0));

        AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);
        AgentSecondJobAdvancementRuntime.tick(entry, agent, 20L, gateway);

        verify(gateway, times(1)).stop(entry);
        verify(gateway, times(2)).navigate(entry, new Point(1_000, 0), true);
    }
}
