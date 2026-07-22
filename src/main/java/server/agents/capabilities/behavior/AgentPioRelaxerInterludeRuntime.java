package server.agents.capabilities.behavior;

import client.Character;
import constants.id.ItemId;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.AgentMovementTickCoordinator;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityState;
import server.agents.plans.AgentPlanPauseRuntime;
import server.agents.plans.amherst.MapleIslandRelaxerSpotCatalog;
import server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Executes the reserved post-Pio chair interlude ahead of ordinary plan work. */
public final class AgentPioRelaxerInterludeRuntime {
    public static final String PAUSE_REASON = "pio-relaxer-interlude";
    private static final long MAX_SPOT_WAIT_MS = 15_000L;
    private static final int ARRIVAL_X_PX = 24;
    private static final int ARRIVAL_Y_PX = 45;
    private static final long TOGGLE_MIN_MS = 900L;
    private static final long TOGGLE_MAX_MS = 1_800L;
    private static final long TOGGLE_DOMAIN = 0x50494F2D544F4747L;

    private AgentPioRelaxerInterludeRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry,
                               Character agent,
                               long nowMs,
                               boolean runAiTick) {
        return tick(entry, agent, nowMs, runAiTick,
                AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        boolean runAiTick,
                        PrimitiveCapabilityGateway gateway) {
        AgentPioRelaxerInterludeState state = entry.capabilityStates()
                .require(AgentPioRelaxerInterludeState.STATE_KEY);
        if (!state.active()) {
            return false;
        }
        if (!gateway.alive(agent)
                || agent.getMapId() != MapleIslandRelaxerSpotCatalog.AMHERST_MAP_ID
                || gateway.itemCount(agent, ItemId.RELAXER) <= 0) {
            finish(entry, agent, state, nowMs, gateway);
            return false;
        }
        if (state.stage() == AgentPioRelaxerInterludeState.Stage.WAITING_FOR_SPOT) {
            int startIndex = Math.floorMod(agent.getId(),
                    MapleIslandRelaxerSpotCatalog.spots(
                            MapleIslandRelaxerSpotCatalog.Pool.AMHERST_NEAR_PIO).size());
            var spot = MapleIslandRelaxerSpotReservationRuntime.reserveNearPio(agent, startIndex);
            if (spot.isEmpty()) {
                gateway.stop(entry);
                if (nowMs - state.requestedAtMs() >= MAX_SPOT_WAIT_MS) {
                    finish(entry, agent, state, nowMs, gateway);
                    return false;
                }
                return true;
            }
            state.assignSpot(new Point(spot.get().x(), spot.get().y()));
        }
        Point target = state.target();
        if (state.stage() == AgentPioRelaxerInterludeState.Stage.MOVING) {
            Point position = gateway.position(agent);
            if (!gateway.grounded(agent) || position == null
                    || Math.abs(position.x - target.x) > ARRIVAL_X_PX
                    || Math.abs(position.y - target.y) > ARRIVAL_Y_PX) {
                if (agent.getChair() > 0) {
                    AgentChairService.stand(entry, agent);
                }
                AgentMovementTickCoordinator.stepMovementCore(entry, target, runAiTick);
                return true;
            }
            gateway.stop(entry);
            AgentFidgetService.clear(entry);
            state.begin(nowMs);
        }
        if (nowMs >= state.resumeAtMs()) {
            finish(entry, agent, state, nowMs, gateway);
            return false;
        }
        gateway.stop(entry);
        if (state.mode() == AgentPioRelaxerInterludeState.Mode.REST) {
            if (agent.getChair() <= 0) {
                gateway.sitChair(agent, ItemId.RELAXER);
            }
            return true;
        }
        if (nowMs >= state.nextToggleAtMs()) {
            if (agent.getChair() > 0) {
                AgentChairService.stand(entry, agent);
            } else if (gateway.sitChair(agent, ItemId.RELAXER)) {
                agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
            }
            state.scheduleToggle(nowMs + toggleDelayMs(entry, state.toggleSequence()));
        }
        return true;
    }

    public static void cancel(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null) {
            return;
        }
        AgentPioRelaxerInterludeState state = entry.capabilityStates()
                .require(AgentPioRelaxerInterludeState.STATE_KEY);
        if (state.active()) {
            finish(entry, agent, state, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
        }
    }

    private static long toggleDelayMs(AgentRuntimeEntry entry, int sequence) {
        long seed = entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).behaviorSeed();
        long width = TOGGLE_MAX_MS - TOGGLE_MIN_MS + 1L;
        return TOGGLE_MIN_MS + Long.remainderUnsigned(
                mix(seed ^ TOGGLE_DOMAIN ^ Integer.toUnsignedLong(sequence)), width);
    }

    private static void finish(AgentRuntimeEntry entry,
                               Character agent,
                               AgentPioRelaxerInterludeState state,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        if (agent != null && agent.getChair() > 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentFidgetService.clear(entry);
        gateway.stop(entry);
        if (agent != null) {
            MapleIslandRelaxerSpotReservationRuntime.release(agent.getId());
        }
        state.clear();
        AgentPlanPauseRuntime.resume(entry, PAUSE_REASON, nowMs);
    }

    private static long mix(long value) {
        long mixed = value ^ (value >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        return mixed ^ (mixed >>> 33);
    }
}
