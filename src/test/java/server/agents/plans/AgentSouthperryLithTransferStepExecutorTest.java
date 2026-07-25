package server.agents.plans;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSouthperryLithTransferStepExecutorTest {
    @Test
    void preservesTheShanksSpawnBeforeReleasingItToTownLife() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        when(agent.getMapId()).thenReturn(AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentPlanDefinition.Step step = new AgentPlanDefinition.Step(
                "transfer", AgentSouthperryLithTransferStepExecutor.OPERATION,
                java.util.List.of("navigation"), Map.of(
                        "npcId", MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID,
                        "destinationMapId", AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID),
                120_000L, 3);
        AgentPlanExecutionContext context = new AgentPlanExecutionContext(
                entry, agent, null, step, AgentPlanStartRequest.EMPTY, 1_000L);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);
            try (var arrivalRuntime = mockStatic(AgentLithHarborArrivalRouteRuntime.class)) {
                AgentSouthperryLithTransferStepExecutor executor =
                        new AgentSouthperryLithTransferStepExecutor();
                executor.start(context);
                AgentPlanStepExecution staged = executor.tick(context);

                assertEquals(AgentPlanExecutionStatus.ACTIVE, staged.status());
                assertEquals(AgentSouthperryLithTransferState.Stage.ARRIVED_IN_LITH,
                        entry.capabilityStates()
                                .require(AgentSouthperryLithTransferState.STATE_KEY)
                                .stage());
                AgentPlanStepExecution released = executor.tick(context);
                assertEquals(AgentPlanExecutionStatus.SUCCEEDED, released.status());
                assertEquals(AgentSouthperryLithTransferState.Stage.TOWNLIFE_READY,
                        entry.capabilityStates()
                                .require(AgentSouthperryLithTransferState.STATE_KEY)
                                .stage());
                arrivalRuntime.verify(() ->
                        AgentLithHarborArrivalRouteRuntime.prepareNavigation(entry, agent));
                arrivalRuntime.verifyNoMoreInteractions();
                verify(gateway).stop(entry);
                verify(gateway, never()).navigate(
                        org.mockito.ArgumentMatchers.eq(entry),
                        org.mockito.ArgumentMatchers.any(Point.class),
                        org.mockito.ArgumentMatchers.anyBoolean());
            }
        }
    }

    @Test
    void acceptsTheNpcSelectedSpawnOnTheTickAfterAnAsynchronousShanksWarp() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(52);
        when(agent.getMapId()).thenReturn(
                MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID,
                MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID,
                AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID);
        when(agent.getChair()).thenReturn(-1);
        Point shanks = new Point(100, 0);
        when(agent.getPosition()).thenReturn(shanks);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID))
                .thenReturn(shanks);
        when(gateway.runNpcScript(
                agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID)).thenReturn(true);
        AgentPlanDefinition.Step step = new AgentPlanDefinition.Step(
                "transfer", AgentSouthperryLithTransferStepExecutor.OPERATION,
                java.util.List.of("navigation"), Map.of(
                        "npcId", MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID,
                        "destinationMapId", AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID),
                120_000L, 3);
        AgentPlanExecutionContext context = new AgentPlanExecutionContext(
                entry, agent, null, step, AgentPlanStartRequest.EMPTY, 2_000L);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);
            try (var arrivalRuntime = mockStatic(AgentLithHarborArrivalRouteRuntime.class)) {
                AgentSouthperryLithTransferStepExecutor executor =
                        new AgentSouthperryLithTransferStepExecutor();
                executor.start(context);
                AgentPlanStepExecution crossing = executor.tick(context);
                AgentPlanStepExecution staged = executor.tick(context);

                assertEquals(AgentPlanExecutionStatus.ACTIVE, crossing.status());
                assertEquals(AgentPlanExecutionStatus.ACTIVE, staged.status());
                verify(gateway).runNpcScript(
                        agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
                arrivalRuntime.verify(() ->
                        AgentLithHarborArrivalRouteRuntime.prepareNavigation(entry, agent));
                arrivalRuntime.verifyNoMoreInteractions();
                assertEquals(AgentSouthperryLithTransferState.Stage.ARRIVED_IN_LITH,
                        entry.capabilityStates()
                                .require(AgentSouthperryLithTransferState.STATE_KEY)
                                .stage());
            }
        }
    }

    @Test
    void yieldsToMovementAfterApproachingShanks() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID);
        when(agent.getChair()).thenReturn(-1);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point shanks = new Point(500, 0);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID))
                .thenReturn(shanks);
        AgentPlanDefinition.Step step = new AgentPlanDefinition.Step(
                "transfer", AgentSouthperryLithTransferStepExecutor.OPERATION,
                java.util.List.of("navigation"), Map.of(
                        "npcId", MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID,
                        "destinationMapId", AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID),
                120_000L, 3);
        AgentPlanExecutionContext context = new AgentPlanExecutionContext(
                entry, agent, null, step, AgentPlanStartRequest.EMPTY, 1_000L);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            AgentPlanStepExecution result =
                    new AgentSouthperryLithTransferStepExecutor().tick(context);

            assertEquals(AgentPlanExecutionStatus.ACTIVE, result.status());
            assertFalse(result.consumed(),
                    "the plan must yield so the shared movement phase can approach Shanks");
            verify(gateway).navigate(entry, shanks, true);
        }
    }
}
