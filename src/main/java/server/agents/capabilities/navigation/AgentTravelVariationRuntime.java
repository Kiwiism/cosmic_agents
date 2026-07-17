package server.agents.capabilities.navigation;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Generic navigation-facing access to the active plan's travel variation. */
public final class AgentTravelVariationRuntime {
    public record RouteVariation(long seed, double maxRouteStretch) {
    }

    private AgentTravelVariationRuntime() {
    }

    public static void configure(AgentRuntimeEntry entry, AgentTravelVariationSettings settings) {
        if (entry != null) entry.travelVariationState().configure(settings);
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry != null) entry.travelVariationState().clear();
    }

    public static AgentTravelVariationSettings settings(AgentRuntimeEntry entry) {
        return entry == null ? AgentTravelVariationSettings.disabled() : entry.travelVariationState().settings();
    }

    public static RouteVariation routeVariation(AgentRuntimeEntry entry,
                                                int mapId,
                                                int targetRegionId,
                                                Point targetPosition) {
        AgentTravelVariationSettings settings = settings(entry);
        if (!settings.routeVariationEnabled() || settings.maxRouteStretch() <= 1.0d) return null;
        long seed = mix(settings.seed() ^ agentId(entry));
        seed = mix(seed ^ mapId);
        seed = mix(seed ^ targetRegionId);
        if (targetPosition != null) seed = mix(seed ^ (((long) targetPosition.x) << 32)
                ^ (targetPosition.y & 0xffffffffL));
        return new RouteVariation(seed, settings.maxRouteStretch());
    }

    public static boolean shouldAttemptTravelHop(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null) return false;
        AgentTravelVariationState.HopDecision decision = entry.travelVariationState().beginHopDecision(nowMs);
        if (decision == null) return false;
        AgentTravelVariationSettings settings = decision.settings();
        if (settings.travelHopProbability() <= 0.0d) return false;
        if (settings.travelHopProbability() >= 1.0d) return true;
        return unitDouble(mix(settings.seed() ^ agentId(entry) ^ decision.sequence()))
                < settings.travelHopProbability();
    }

    public static void markTravelHopStarted(AgentRuntimeEntry entry, long nowMs) {
        if (entry != null) entry.travelVariationState().markHopStarted(nowMs);
    }

    static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    static double unitDouble(long seed) {
        return (mix(seed) >>> 11) * 0x1.0p-53;
    }

    private static long agentId(AgentRuntimeEntry entry) {
        return entry != null && AgentRuntimeIdentityRuntime.hasBot(entry)
                ? AgentRuntimeIdentityRuntime.bot(entry).getId() : 0L;
    }
}
