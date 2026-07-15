package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.movement.AgentRelaxerSpotCatalog;
import server.agents.capabilities.movement.AgentRelaxerSpotReservationRuntime;

import java.awt.Point;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class PlanStopObjectiveCapability
        implements AgentExecutableCapability<PlanStopObjectiveCapability.Command> {
    private static final int REST_SPOT_ARRIVAL_RANGE_PX = 8;

    public record Command(String objectiveId,
                          int finalMapId,
                          Map<Integer, Integer> expectedQuestStatuses,
                          Set<Integer> forbiddenCompletedQuestIds,
                          String reason,
                          Integer chairItemId,
                          AgentRelaxerSpotCatalog.Pool restSpotPool) implements AgentCapabilityCommand {
        public Command(String objectiveId,
                       int finalMapId,
                       Map<Integer, Integer> expectedQuestStatuses,
                       Set<Integer> forbiddenCompletedQuestIds,
                       String reason,
                       Integer chairItemId) {
            this(objectiveId, finalMapId, expectedQuestStatuses, forbiddenCompletedQuestIds,
                    reason, chairItemId, null);
        }

        public Command(String objectiveId,
                       int finalMapId,
                       Set<Integer> forbiddenCompletedQuestIds,
                       String reason,
                       Integer chairItemId) {
            this(objectiveId, finalMapId, Map.of(), forbiddenCompletedQuestIds, reason, chairItemId, null);
        }

        public Command(String objectiveId,
                       int finalMapId,
                       Set<Integer> forbiddenCompletedQuestIds,
                       String reason) {
            this(objectiveId, finalMapId, Map.of(), forbiddenCompletedQuestIds, reason, null, null);
        }

        public Command {
            expectedQuestStatuses = expectedQuestStatuses == null
                    ? Map.of() : Map.copyOf(expectedQuestStatuses);
            forbiddenCompletedQuestIds = forbiddenCompletedQuestIds == null
                    ? Set.of() : Set.copyOf(forbiddenCompletedQuestIds);
            if (objectiveId == null || objectiveId.isBlank() || finalMapId <= 0
                    || reason == null || reason.isBlank()
                    || chairItemId != null && chairItemId <= 0
                    || restSpotPool != null && restSpotPool.mapId() != finalMapId) {
                throw new IllegalArgumentException("plan-stop parameters are required");
            }
        }

        @Override
        public String type() {
            return "plan-stop-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public PlanStopObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public PlanStopObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    public PlanStopObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                       AmherstScopePolicy scopePolicy) {
        support = new AmherstObjectiveCapabilitySupport(gateway, scopePolicy);
    }

    @Override
    public String id() {
        return "plan-stop-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            AgentRelaxerSpotReservationRuntime.release(context.agent().getId());
            return failure;
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            AgentCapabilityStep travel = support.travel(context, command.finalMapId());
            if (travel != null) {
                return travel;
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.finalState(command.finalMapId(),
                            command.expectedQuestStatuses(), java.util.Map.of(),
                            command.forbiddenCompletedQuestIds()),
                    "plan stop requests final live-state verification");
        }
        if (command.chairItemId() == null) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), command.reason()));
        }
        if (support.gateway().itemCount(context.agent(), command.chairItemId()) < 1) {
            return AmherstObjectiveCapabilitySupport.missing(
                    "Pio's Relaxer reward is required before the showcase can finish");
        }
        if (command.restSpotPool() != null) {
            AgentCapabilityStep restApproach = approachRestSpot(context, command);
            if (restApproach != null) {
                return restApproach;
            }
            Point position = support.gateway().position(context.agent());
            int facingDirection = context.memory().intValue("restFacingDirection", 1);
            support.gateway().facePosition(context.agent(),
                    new Point(position.x + facingDirection, position.y));
        }
        if (!support.gateway().sitChair(context.agent(), command.chairItemId())) {
            AgentRelaxerSpotReservationRuntime.release(context.agent().getId());
            context.memory().putBoolean("restTargetSelected", false);
            return AgentCapabilityStep.retry("Agent could not sit on the Relaxer");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), command.reason() + " Agent is resting on the Relaxer."));
    }

    private AgentCapabilityStep approachRestSpot(AgentCapabilityContext context, Command command) {
        if (!context.memory().booleanValue("restTargetSelected", false)) {
            var reserved = AgentRelaxerSpotReservationRuntime.reserveRandom(
                    context.agent(), command.restSpotPool());
            if (reserved.isEmpty()) {
                return AgentCapabilityStep.retry("No unoccupied Relaxer spot is available");
            }
            AgentRelaxerSpotCatalog.Spot spot = reserved.get();
            context.memory().putInt("restTargetX", spot.x());
            context.memory().putInt("restTargetY", spot.y());
            context.memory().putInt("restFacingDirection",
                    ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            context.memory().putBoolean("restTargetSelected", true);
        }
        Point destination = new Point(
                context.memory().intValue("restTargetX", 0),
                context.memory().intValue("restTargetY", 0));
        return support.approachPosition(
                context, command.finalMapId(), destination, REST_SPOT_ARRIVAL_RANGE_PX);
    }
}
