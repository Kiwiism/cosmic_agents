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
    private static final long REST_SETTLE_DELAY_MS = 250L;
    private static final long CHAIR_VERIFY_DELAY_MS = 250L;

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
        // Once sitting has been issued, no approach or facing packet may run: either one can
        // replace the client's sit stance even though the server-side chair id remains active.
        boolean chairActive = support.gateway().chairItemId(context.agent()) == command.chairItemId();
        if (context.memory().booleanValue("chairSitIssued", false)) {
            if (context.nowMs() < context.memory().longValue("chairVerifyAtMs", context.nowMs())) {
                return AgentCapabilityStep.running("verifying Relaxer state", true);
            }
            if (chairActive) {
                return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                        command.objectiveId(), command.reason() + " Agent is resting on the Relaxer."));
            }
            context.memory().putBoolean("chairSitIssued", false);
            return AgentCapabilityStep.running("Relaxer state was interrupted; sitting again", true);
        }
        if (chairActive) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), command.reason() + " Agent is resting on the Relaxer."));
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
            if (!context.memory().booleanValue("restSettleStarted", false)) {
                context.memory().putBoolean("restSettleStarted", true);
                context.memory().putLong("restSettleReadyAtMs", context.nowMs() + REST_SETTLE_DELAY_MS);
                return AgentCapabilityStep.running("settling at reserved Relaxer spot", false);
            }
            if (context.nowMs() < context.memory().longValue("restSettleReadyAtMs", context.nowMs())) {
                return AgentCapabilityStep.running("settling at reserved Relaxer spot", false);
            }
        }
        if (!support.gateway().sitChair(context.agent(), command.chairItemId())) {
            AgentRelaxerSpotReservationRuntime.release(context.agent().getId());
            context.memory().putBoolean("restTargetSelected", false);
            context.memory().putBoolean("restSettleStarted", false);
            return AgentCapabilityStep.retry("Agent could not sit on the Relaxer");
        }
        context.memory().putBoolean("chairSitIssued", true);
        context.memory().putLong("chairVerifyAtMs", context.nowMs() + CHAIR_VERIFY_DELAY_MS);
        return AgentCapabilityStep.running("verifying Relaxer state", true);
    }

    private AgentCapabilityStep approachRestSpot(AgentCapabilityContext context, Command command) {
        if (!context.memory().booleanValue("restTargetSelected", false)) {
            int candidateCount = AgentRelaxerSpotCatalog.spots(command.restSpotPool()).size();
            var controlledIndex = MapleIslandObjectiveRandomnessRuntime.selectRestSpotIndex(
                    context.entry(), command.finalMapId(), candidateCount);
            var reserved = controlledIndex.isPresent()
                    ? AgentRelaxerSpotReservationRuntime.reserveFromIndex(
                    context.agent(), command.restSpotPool(), controlledIndex.getAsInt())
                    : AgentRelaxerSpotReservationRuntime.reserveRandom(
                    context.agent(), command.restSpotPool());
            if (reserved.isEmpty()) {
                return AgentCapabilityStep.retry("No unoccupied Relaxer spot is available");
            }
            AgentRelaxerSpotCatalog.Spot spot = reserved.get();
            context.memory().putInt("restTargetX", spot.x());
            context.memory().putInt("restTargetY", spot.y());
            int facingDirection = MapleIslandObjectiveRandomnessRuntime
                    .selectRestFacingDirection(context.entry(), command.finalMapId())
                    .orElseGet(() -> ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            context.memory().putInt("restFacingDirection", facingDirection);
            context.memory().putBoolean("restTargetSelected", true);
        }
        Point destination = new Point(
                context.memory().intValue("restTargetX", 0),
                context.memory().intValue("restTargetY", 0));
        return support.approachPosition(
                context, command.finalMapId(), destination, REST_SPOT_ARRIVAL_RANGE_PX);
    }
}
