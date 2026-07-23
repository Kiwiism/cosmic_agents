package server.agents.capabilities.movement.fidget;

import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.profiles.AgentBehaviorProfileRuntime;

import java.awt.Point;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentProfileNavigationFidgetPolicy {
    private static final Set<AgentFidgetMode> SAFE_STATIONARY_MODES = EnumSet.of(
            AgentFidgetMode.WAIT,
            AgentFidgetMode.PRONE,
            AgentFidgetMode.SPAM_PRONE);

    private AgentProfileNavigationFidgetPolicy() {
    }

    public static boolean tick(AgentCapabilityContext context,
                               Point destination,
                               PrimitiveCapabilityGateway gateway) {
        if (context == null || destination == null || gateway == null) {
            return false;
        }
        if (AgentFidgetStateRuntime.trigger(context.entry()) == AgentFidgetTrigger.PROFILE_NAVIGATION) {
            return AgentFidgetService.tryHandleProfileNavigationTick(
                    context.entry(), destination, context.nowMs());
        }

        AgentBehaviorProfile.Movement movement = AgentBehaviorProfileRuntime.current(context.entry())
                .map(profile -> profile.presentation().movement())
                .orElse(null);
        List<AgentFidgetMode> allowedModes = safeModes(movement);
        if (movement == null || !movement.navigationFidgetsEnabled() || allowedModes.isEmpty()) {
            return false;
        }

        long nextAt = context.entry().behaviorProfileState().nextNavigationFidgetAtMs();
        if (nextAt == 0L) {
            scheduleNext(context, movement, context.nowMs());
            return false;
        }
        if (context.nowMs() < nextAt || !safeToStart(context, gateway)) {
            return false;
        }

        AgentFidgetMode mode = allowedModes.get(ThreadLocalRandom.current().nextInt(allowedModes.size()));
        int durationMs = (int) AgentBehaviorProfileRuntime.sample(movement.fidgetDurationMs());
        gateway.stop(context.entry());
        AgentFidgetService.startFidget(
                context.entry(), mode, context.nowMs(), durationMs, AgentFidgetTrigger.PROFILE_NAVIGATION);
        scheduleNext(context, movement, context.nowMs() + durationMs);
        return AgentFidgetService.tryHandleProfileNavigationTick(
                context.entry(), destination, context.nowMs());
    }

    public static void clear(AgentCapabilityContext context) {
        if (context != null) {
            AgentFidgetService.clearProfileNavigation(context.entry());
        }
    }

    static List<AgentFidgetMode> safeModes(AgentBehaviorProfile.Movement movement) {
        if (movement == null) {
            return List.of();
        }
        List<AgentFidgetMode> modes = new ArrayList<>();
        for (AgentBehaviorProfile.NavigationFidget configured : movement.navigationFidgetModes()) {
            AgentFidgetMode mode = switch (configured) {
                case WAIT -> AgentFidgetMode.WAIT;
                case PRONE -> AgentFidgetMode.PRONE;
                case PRONE_TAP -> AgentFidgetMode.SPAM_PRONE;
            };
            if (SAFE_STATIONARY_MODES.contains(mode)) {
                modes.add(mode);
            }
        }
        return List.copyOf(modes);
    }

    private static boolean safeToStart(AgentCapabilityContext context, PrimitiveCapabilityGateway gateway) {
        return gateway.grounded(context.agent())
                && !AgentMovementStateRuntime.inAir(context.entry())
                && !AgentMovementStateRuntime.climbing(context.entry())
                && !AgentMovementStateRuntime.downJumpPending(context.entry())
                && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(context.entry())
                && AgentFidgetStateRuntime.inactive(context.entry());
    }

    private static void scheduleNext(AgentCapabilityContext context,
                                     AgentBehaviorProfile.Movement movement,
                                     long fromMs) {
        long cooldownMs = AgentBehaviorProfileRuntime.sample(movement.fidgetCooldownMs());
        context.entry().behaviorProfileState().setNextNavigationFidgetAtMs(fromMs + cooldownMs);
    }
}
