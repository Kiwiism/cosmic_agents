package server.agents.plans;

import client.Character;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;

import java.awt.Point;

public final class AgentSouthperryLithTransferStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "southperry-lith-transfer";
    private static final int INTERACTION_DISTANCE_PX = config.AgentTuning.intValue("server.agents.plans.AgentSouthperryLithTransferStepExecutor.INTERACTION_DISTANCE_PX");

    @Override
    public String operation() {
        return OPERATION;
    }

    @Override
    public void validateDefinition(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        if (!(step.parameters().get("npcId") instanceof Number npc) || npc.intValue() <= 0
                || !(step.parameters().get("destinationMapId") instanceof Number destination)
                || destination.intValue() <= 0) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId()
                            + " requires positive npcId and destinationMapId");
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        context.entry().capabilityStates()
                .require(AgentSouthperryLithTransferState.STATE_KEY)
                .crossing();
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        Character agent = context.agent();
        PrimitiveCapabilityGateway gateway = AgentPrimitiveCapabilityGatewayRuntime.gateway();
        AgentSouthperryLithTransferState state = context.entry().capabilityStates()
                .require(AgentSouthperryLithTransferState.STATE_KEY);
        if (agent.getMapId() == AgentLithHarborArrivalRouteRuntime.LITH_HARBOR_MAP_ID) {
            if (state.stage() == AgentSouthperryLithTransferState.Stage.CROSSING) {
                /*
                 * Shanks' script has already selected and broadcast the real Lith Harbor
                 * arrival point. Preserve it: TownLife owns all movement from that point.
                 */
                AgentLithHarborArrivalRouteRuntime.prepareNavigation(context.entry(), agent);
                state.arrivedInLith();
                return AgentPlanStepExecution.active(true);
            }
            gateway.stop(context.entry());
            state.townLifeReady();
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    "arrived in Lith Harbor for TownLife navigation");
        }
        if (agent.getMapId() != MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Southperry-to-Lith transfer requires Southperry or Lith Harbor");
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(context.entry(), agent);
            return AgentPlanStepExecution.active(true);
        }
        int npcId = intParameter(context.step(), "npcId",
                MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        Point shanks = gateway.npcPosition(agent, npcId);
        if (shanks == null) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Shanks is unavailable in Southperry");
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(shanks)
                > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(context.entry(), shanks, true);
            return AgentPlanStepExecution.active(false);
        }
        gateway.stop(context.entry());
        gateway.facePosition(agent, shanks);
        gateway.runNpcScript(agent, npcId);
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(context.entry());
        context.entry().capabilityStates()
                .remove(AgentSouthperryLithTransferState.STATE_KEY);
    }

    private static int intParameter(AgentPlanDefinition.Step step, String key, int fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
