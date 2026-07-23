package server.agents.plans.amherst;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.objective.AgentObjectiveResult;
import server.agents.capabilities.objective.AgentPlanCompletionBehavior;
import server.agents.capabilities.objective.AgentPlanCompletionMode;
import server.agents.capabilities.primitive.AgentNavigationCapability;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Maple Island's Relaxer/idle/wander presentation after the plan is complete. */
public final class MapleIslandPlanCompletionBehavior implements AgentPlanCompletionBehavior {
    private static final int ARRIVAL_RANGE_PX = config.AgentTuning.intValue("server.agents.plans.amherst.MapleIslandPlanCompletionBehavior.ARRIVAL_RANGE_PX");
    private static final long REST_SETTLE_DELAY_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandPlanCompletionBehavior.REST_SETTLE_DELAY_MS");
    private static final long CHAIR_VERIFY_DELAY_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandPlanCompletionBehavior.CHAIR_VERIFY_DELAY_MS");
    private static final long NAVIGATION_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandPlanCompletionBehavior.NAVIGATION_TIMEOUT_MS");

    private final PrimitiveCapabilityGateway gateway;
    private final Integer chairItemId;
    private final MapleIslandRelaxerSpotCatalog.Pool restSpotPool;
    private final MapleIslandPlanCompletionPolicy policy;

    public MapleIslandPlanCompletionBehavior(PrimitiveCapabilityGateway gateway,
                                              Integer chairItemId,
                                              MapleIslandRelaxerSpotCatalog.Pool restSpotPool) {
        this.gateway = gateway;
        this.chairItemId = chairItemId;
        this.restSpotPool = restSpotPool;
        this.policy = MapleIslandPlanCompletionPolicy.INSTANCE;
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, String objectiveId, String reason) {
        if (chairItemId == null) return success(objectiveId, reason);
        if (gateway.itemCount(context.agent(), chairItemId) < 1) {
            return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                    AgentCapabilityStatus.MISSING_REQUIREMENT,
                    AgentCapabilityReasonCode.MISSING_REQUIREMENT,
                    "Pio's Relaxer reward is required before the showcase can finish"));
        }
        AgentPlanCompletionMode mode = restSpotPool == null
                ? AgentPlanCompletionMode.SIT : policy.selectMode(context.entry(), gateway.mapId(context.agent()));
        if (mode != AgentPlanCompletionMode.SIT) {
            AgentCapabilityStep approach = approachRestSpot(context, mode);
            if (approach != null) return approach;
            faceRestDirection(context);
            String location = policy.locationName(context.entry(), gateway.mapId(context.agent()));
            String ending;
            if (mode == AgentPlanCompletionMode.WANDER
                    && policy.startWander(context.entry(), context.agent())) {
                MapleIslandRelaxerSpotReservationRuntime.release(context.agent().getId());
                ending = " Agent is wandering around " + location + ".";
            } else {
                gateway.stop(context.entry());
                ending = mode == AgentPlanCompletionMode.FIDGET
                        ? " Agent is idling with occasional fidgets in " + location + "."
                        : " Agent is idling in " + location + ".";
            }
            return success(objectiveId, reason + ending);
        }
        boolean chairActive = gateway.chairItemId(context.agent()) == chairItemId;
        if (context.memory().booleanValue("chairSitIssued", false)) {
            if (context.nowMs() < context.memory().longValue("chairVerifyAtMs", context.nowMs()))
                return AgentCapabilityStep.running("verifying Relaxer state", true);
            if (chairActive) return success(objectiveId, reason + " Agent is resting on the Relaxer.");
            context.memory().putBoolean("chairSitIssued", false);
            return AgentCapabilityStep.running("Relaxer state was interrupted; sitting again", true);
        }
        if (chairActive) return success(objectiveId, reason + " Agent is resting on the Relaxer.");
        if (restSpotPool != null) {
            AgentCapabilityStep approach = approachRestSpot(context, mode);
            if (approach != null) return approach;
            faceRestDirection(context);
            if (!context.memory().booleanValue("restSettleStarted", false)) {
                context.memory().putBoolean("restSettleStarted", true);
                context.memory().putLong("restSettleReadyAtMs", context.nowMs() + REST_SETTLE_DELAY_MS);
                return AgentCapabilityStep.running("settling at reserved Relaxer spot", false);
            }
            if (context.nowMs() < context.memory().longValue("restSettleReadyAtMs", context.nowMs()))
                return AgentCapabilityStep.running("settling at reserved Relaxer spot", false);
        }
        if (!gateway.sitChair(context.agent(), chairItemId)) {
            abort(context);
            context.memory().putBoolean("restTargetSelected", false);
            context.memory().putBoolean("restSettleStarted", false);
            return AgentCapabilityStep.retry("Agent could not sit on the Relaxer");
        }
        context.memory().putBoolean("chairSitIssued", true);
        context.memory().putLong("chairVerifyAtMs", context.nowMs() + CHAIR_VERIFY_DELAY_MS);
        return AgentCapabilityStep.running("verifying Relaxer state", true);
    }

    @Override public void abort(AgentCapabilityContext context) {
        MapleIslandRelaxerSpotReservationRuntime.release(context.agent().getId());
    }

    private AgentCapabilityStep approachRestSpot(
            AgentCapabilityContext context,
            AgentPlanCompletionMode mode) {
        if (!context.memory().booleanValue("restTargetSelected", false)) {
            int mapId = gateway.mapId(context.agent());
            MapleIslandRelaxerSpotCatalog.Pool selectedPool = policy.selectRestSpotPool(
                    context.entry(), mapId, mode, restSpotPool);
            Optional<MapleIslandRelaxerSpotCatalog.Spot> reserved = reserveRestSpot(
                    context, selectedPool, mapId);
            if (reserved.isEmpty() && selectedPool != restSpotPool) {
                reserved = reserveRestSpot(context, restSpotPool, mapId);
            }
            if (reserved.isEmpty()) return AgentCapabilityStep.retry("No unoccupied Relaxer spot is available");
            var spot = reserved.get();
            context.memory().putInt("restTargetX", spot.x());
            context.memory().putInt("restTargetY", spot.y());
            context.memory().putInt("restFacingDirection", policy
                    .selectFacingDirection(context.entry(), gateway.mapId(context.agent()))
                    .orElseGet(() -> ThreadLocalRandom.current().nextBoolean() ? 1 : -1));
            context.memory().putBoolean("restTargetSelected", true);
        }
        Point destination = new Point(context.memory().intValue("restTargetX", 0),
                context.memory().intValue("restTargetY", 0));
        if (gateway.position(context.agent()).distanceSq(destination) <= (long) ARRIVAL_RANGE_PX * ARRIVAL_RANGE_PX)
            return gateway.grounded(context.agent()) ? null
                    : AgentCapabilityStep.running("waiting to land at Relaxer spot", false);
        return AgentCapabilityStep.handoff(new AgentCapabilityInvocation<>(
                new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(gateway.mapId(context.agent()), destination,
                        ARRIVAL_RANGE_PX, true), NAVIGATION_TIMEOUT_MS, 2),
                "Maple Island completion requests navigation to a Relaxer spot");
    }

    private Optional<MapleIslandRelaxerSpotCatalog.Spot> reserveRestSpot(
            AgentCapabilityContext context,
            MapleIslandRelaxerSpotCatalog.Pool pool,
            int mapId) {
        int count = MapleIslandRelaxerSpotCatalog.spots(pool).size();
        var selected = policy.selectRestSpotIndex(context.entry(), mapId, count);
        return selected.isPresent()
                ? MapleIslandRelaxerSpotReservationRuntime.reserveFromIndex(
                context.agent(), pool, selected.getAsInt())
                : MapleIslandRelaxerSpotReservationRuntime.reserveRandom(context.agent(), pool);
    }

    private void faceRestDirection(AgentCapabilityContext context) {
        Point position = gateway.position(context.agent());
        gateway.facePosition(context.agent(), new Point(position.x
                + context.memory().intValue("restFacingDirection", 1), position.y));
    }

    private static AgentCapabilityStep success(String objectiveId, String message) {
        return AgentCapabilityStep.terminal(AgentCapabilityResult.success(
                message, new AgentObjectiveResult(objectiveId, message)));
    }
}
