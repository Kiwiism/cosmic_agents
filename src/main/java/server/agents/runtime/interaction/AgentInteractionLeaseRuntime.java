package server.agents.runtime.interaction;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.activity.AgentActivityTick;
import server.agents.runtime.field.AgentFieldActivityRuntime;
import server.agents.runtime.field.AgentFieldActivityState;

/** Coordinates bounded chat/trade interruptions without transferring parent-activity ownership. */
public final class AgentInteractionLeaseRuntime {
    private static final String TUNING_PREFIX =
            "server.agents.runtime.interaction.AgentInteractionLeaseRuntime.";
    private static final long CHAT_MIN_HOLD_MS = tuningLong("CHAT_MIN_HOLD_MS");
    private static final long CHAT_TIMEOUT_MS = tuningLong("CHAT_TIMEOUT_MS");
    private static final long TRADE_TIMEOUT_MS = tuningLong("TRADE_TIMEOUT_MS");

    private AgentInteractionLeaseRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentInteractionLeaseState.STATE_KEY)
                .map(AgentInteractionLeaseState::active).orElse(false);
    }

    public static String beginChat(AgentRuntimeEntry entry,
                                   Character agent,
                                   int participantCharacterId,
                                   long nowMs) {
        return begin(entry, agent, AgentInteractionLeaseState.Type.CHAT,
                participantCharacterId, nowMs, CHAT_MIN_HOLD_MS, CHAT_TIMEOUT_MS);
    }

    public static String beginTrade(AgentRuntimeEntry entry,
                                    Character agent,
                                    int participantCharacterId,
                                    long nowMs) {
        return begin(entry, agent, AgentInteractionLeaseState.Type.TRADE,
                participantCharacterId, nowMs, 0L, TRADE_TIMEOUT_MS);
    }

    private static String begin(AgentRuntimeEntry entry,
                                Character agent,
                                AgentInteractionLeaseState.Type type,
                                int participantCharacterId,
                                long nowMs,
                                long minimumDurationMs,
                                long timeoutMs) {
        Parent parent = parent(entry);
        if (entry == null || agent == null || type == null || parent == null) {
            return "";
        }
        AgentInteractionLeaseState state = entry.capabilityStates()
                .require(AgentInteractionLeaseState.STATE_KEY);
        AgentInteractionLeaseState.Snapshot previous = state.snapshot();
        if (previous.active() && previous.type() == AgentInteractionLeaseState.Type.TRADE
                && type == AgentInteractionLeaseState.Type.CHAT) {
            return previous.interactionId();
        }
        String id = state.begin(type, participantCharacterId,
                parent.activityId(), parent.sessionId(), nowMs,
                minimumDurationMs, timeoutMs);
        if (!previous.active() || !previous.interactionId().equals(id)) {
            suspendParent(entry, agent, parent, nowMs);
            publish(entry, agent, state.snapshot(), AgentInteractionLeaseEvent.Phase.STARTED,
                    "nested " + type.name().toLowerCase(java.util.Locale.ROOT), nowMs);
        }
        return id;
    }

    public static void complete(AgentRuntimeEntry entry,
                                AgentInteractionLeaseState.Type type) {
        if (entry == null) {
            return;
        }
        AgentInteractionLeaseState state = entry.capabilityStates()
                .require(AgentInteractionLeaseState.STATE_KEY);
        AgentInteractionLeaseState.Snapshot snapshot = state.snapshot();
        if (snapshot.active() && (type == null || snapshot.type() == type)) {
            state.markComplete();
        }
    }

    /** Called from the common tick before foreground arbitration. */
    public static void reconcileTrade(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return;
        }
        AgentInteractionLeaseState state = entry.capabilityStates()
                .require(AgentInteractionLeaseState.STATE_KEY);
        AgentInteractionLeaseState.Snapshot snapshot = state.snapshot();
        if (snapshot.active() && !parentMatches(entry, snapshot)) {
            finish(entry, agent, nowMs, AgentInteractionLeaseEvent.Phase.CANCELLED,
                    "parent activity session ended");
            return;
        }
        if (agent.getTrade() != null && parent(entry) != null) {
            int partnerId = agent.getTrade().getPartner() == null
                    ? 0 : agent.getTrade().getPartner().getChr().getId();
            beginTrade(entry, agent, partnerId, nowMs);
        } else if (snapshot.active()
                && snapshot.type() == AgentInteractionLeaseState.Type.TRADE
                && agent.getTrade() == null) {
            state.markComplete();
        }
    }

    public static AgentActivityTick tick(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentInteractionLeaseState state = entry.capabilityStates()
                .require(AgentInteractionLeaseState.STATE_KEY);
        AgentInteractionLeaseState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active()) {
            return AgentActivityTick.PASS;
        }
        if (!parentMatches(entry, snapshot)) {
            finish(entry, agent, nowMs, AgentInteractionLeaseEvent.Phase.CANCELLED,
                    "parent activity session ended");
            return AgentActivityTick.PASS;
        }
        if (snapshot.type() == AgentInteractionLeaseState.Type.TRADE
                && agent.getTrade() == null) {
            state.markComplete();
        }
        if (state.readyToRelease(nowMs)) {
            boolean timedOut = !state.snapshot().operationComplete();
            finish(entry, agent, nowMs,
                    timedOut ? AgentInteractionLeaseEvent.Phase.TIMED_OUT
                            : AgentInteractionLeaseEvent.Phase.COMPLETED,
                    timedOut ? "interaction deadline elapsed" : "interaction completed");
            return AgentActivityTick.PASS;
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        return AgentActivityTick.CONSUMED;
    }

    public static void cancel(AgentRuntimeEntry entry,
                              Character agent,
                              String reason,
                              long nowMs) {
        if (active(entry)) {
            finish(entry, agent, nowMs, AgentInteractionLeaseEvent.Phase.CANCELLED, reason);
        }
    }

    private static void finish(AgentRuntimeEntry entry,
                               Character agent,
                               long nowMs,
                               AgentInteractionLeaseEvent.Phase phase,
                               String reason) {
        AgentInteractionLeaseState state = entry.capabilityStates()
                .require(AgentInteractionLeaseState.STATE_KEY);
        AgentInteractionLeaseState.Snapshot snapshot = state.snapshot();
        if (!snapshot.active()) {
            return;
        }
        state.clear();
        resumeParent(entry, agent, snapshot, nowMs);
        publish(entry, agent, snapshot, phase, reason, nowMs);
    }

    private static void publish(AgentRuntimeEntry entry,
                                Character agent,
                                AgentInteractionLeaseState.Snapshot snapshot,
                                AgentInteractionLeaseEvent.Phase phase,
                                String reason,
                                long nowMs) {
        if (agent == null || snapshot == null || !snapshot.active()) {
            return;
        }
        AgentSessionEventRuntime.bus(entry).publish(new AgentInteractionLeaseEvent(
                agent.getId(), nowMs, snapshot.interactionId(),
                snapshot.type(), snapshot.participantCharacterId(), snapshot.parentSessionId(),
                phase, reason));
    }

    private static Parent parent(AgentRuntimeEntry entry) {
        if (entry == null) return null;
        if (AgentTownLifeRuntime.active(entry)) {
            AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
            return new Parent("town-life", state.sessionId());
        }
        AgentFieldActivityState.Snapshot field = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY).snapshot();
        return field.active() ? new Parent(AgentFieldActivityRuntime.ACTIVITY_ID,
                field.handle().sessionId()) : null;
    }

    private static boolean parentMatches(
            AgentRuntimeEntry entry, AgentInteractionLeaseState.Snapshot snapshot) {
        Parent parent = parent(entry);
        return parent != null && parent.activityId().equals(snapshot.parentActivityId())
                && parent.sessionId().equals(snapshot.parentSessionId());
    }

    private static void suspendParent(
            AgentRuntimeEntry entry, Character agent, Parent parent, long nowMs) {
        if ("town-life".equals(parent.activityId())) {
            AgentTownLifeRuntime.suspendForExternalInteraction(entry, agent, nowMs);
        } else {
            AgentFieldActivityRuntime.suspend(entry, agent, "external interaction", nowMs);
        }
    }

    private static void resumeParent(
            AgentRuntimeEntry entry, Character agent,
            AgentInteractionLeaseState.Snapshot snapshot, long nowMs) {
        if ("town-life".equals(snapshot.parentActivityId())) {
            AgentTownLifeRuntime.resumeAfterExternalInteraction(entry, nowMs);
        } else if (AgentFieldActivityRuntime.ACTIVITY_ID.equals(snapshot.parentActivityId())) {
            AgentFieldActivityRuntime.resume(entry, agent, "external interaction completed", nowMs);
        }
    }

    private record Parent(String activityId, String sessionId) {
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }
}
