package server.agents.capabilities.navigation;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Runtime hook used by a Maple Island cohort command to configure one Agent. */
public final class AgentMapleIslandTravelRuntime {
    public record RouteVariation(long seed, double maxRouteStretch) {
    }

    private AgentMapleIslandTravelRuntime() {
    }

    public static void configure(AgentRuntimeEntry entry, AgentMapleIslandTravelSettings settings) {
        if (entry != null) {
            AgentTravelVariationRuntime.configure(entry, toGeneric(settings));
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry != null) {
            AgentTravelVariationRuntime.clear(entry);
        }
    }

    public static AgentMapleIslandTravelSettings settings(AgentRuntimeEntry entry) {
        return entry == null
                ? AgentMapleIslandTravelSettings.disabled()
                : toMapleIsland(AgentTravelVariationRuntime.settings(entry));
    }

    public static RouteVariation routeVariation(AgentRuntimeEntry entry,
                                                int mapId,
                                                int targetRegionId,
                                                Point targetPosition) {
        AgentTravelVariationRuntime.RouteVariation variation =
                AgentTravelVariationRuntime.routeVariation(entry, mapId, targetRegionId, targetPosition);
        return variation == null ? null : new RouteVariation(variation.seed(), variation.maxRouteStretch());
    }

    public static boolean shouldAttemptTravelHop(AgentRuntimeEntry entry,
                                                  long nowMs) {
        return AgentTravelVariationRuntime.shouldAttemptTravelHop(entry, nowMs);
    }

    public static void markTravelHopStarted(AgentRuntimeEntry entry, long nowMs) {
        if (entry != null) {
            AgentTravelVariationRuntime.markTravelHopStarted(entry, nowMs);
        }
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

    private static AgentTravelVariationSettings toGeneric(AgentMapleIslandTravelSettings settings) {
        return new AgentTravelVariationSettings(settings.seed(), settings.routeVariationEnabled(),
                settings.maxRouteStretch(), settings.travelHopsEnabled(), settings.travelHopProbability(),
                settings.travelHopDecisionIntervalMs(), settings.travelHopCooldownMs());
    }

    private static AgentMapleIslandTravelSettings toMapleIsland(AgentTravelVariationSettings settings) {
        return new AgentMapleIslandTravelSettings(settings.seed(), settings.routeVariationEnabled(),
                settings.maxRouteStretch(), settings.travelHopsEnabled(), settings.travelHopProbability(),
                settings.travelHopDecisionIntervalMs(), settings.travelHopCooldownMs());
    }
}
