package server.agents.capabilities.objective;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.profiles.AgentBehaviorProfile;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Plan-neutral access to one Agent's configured objective variation. */
public final class AgentObjectiveVariationRuntime {
    private AgentObjectiveVariationRuntime() {}

    public static void configure(AgentRuntimeEntry entry, AgentObjectiveVariationSettings settings) {
        if (entry != null) entry.objectiveVariationState().configure(settings, agentId(entry));
    }
    public static void clear(AgentRuntimeEntry entry) { configure(entry, AgentObjectiveVariationSettings.disabled()); }
    public static AgentObjectiveVariationSettings settings(AgentRuntimeEntry entry) {
        return entry == null ? AgentObjectiveVariationSettings.disabled() : entry.objectiveVariationState().settings();
    }
    public static OptionalLong sampleNpcInteractionDelayMs(AgentRuntimeEntry entry, AgentBehaviorProfile.DelayRange fallback) {
        return settings(entry).enabled() ? OptionalLong.of(entry.objectiveVariationState().sampleNpcDelay(fallback)) : OptionalLong.empty();
    }
    public static OptionalLong sampleBetweenObjectivesDelayMs(AgentRuntimeEntry entry, AgentBehaviorProfile.DelayRange fallback) {
        return settings(entry).enabled() ? OptionalLong.of(entry.objectiveVariationState().sampleObjectiveDelay(fallback)) : OptionalLong.empty();
    }
    public static OptionalInt selectNpcAnchorIndex(AgentRuntimeEntry entry, int mapId, int npcId, int count) {
        AgentObjectiveVariationSettings settings = settings(entry);
        return settings.enabled() && settings.npcAnchorVariationEnabled() && count > 0
                ? OptionalInt.of(entry.objectiveVariationState().selectNpcAnchorIndex(mapId, npcId, count))
                : OptionalInt.empty();
    }
    public static void rememberNpcInteractionPosition(AgentRuntimeEntry entry, int mapId, int npcId,
                                                      Point position, boolean climbable) {
        if (entry != null) {
            entry.objectiveVariationState().rememberNpcInteractionPosition(
                    mapId, npcId, position, climbable);
        }
    }
    public static boolean canReuseNpcInteractionPosition(AgentRuntimeEntry entry, int mapId, int npcId,
                                                         Point currentPosition, int tolerancePx) {
        return entry != null && entry.objectiveVariationState().canReuseNpcInteractionPosition(
                mapId, npcId, currentPosition, tolerancePx);
    }
    public static boolean lastNpcInteractionWasClimbable(AgentRuntimeEntry entry, int mapId, int npcId) {
        return entry != null && entry.objectiveVariationState()
                .lastNpcInteractionWasClimbable(mapId, npcId);
    }
    public static OptionalLong sampleCashShopVisitDelayMs(AgentRuntimeEntry entry, long min, long max) {
        return settings(entry).enabled()
                ? OptionalLong.of(entry.objectiveVariationState().sampleCashShopVisitDelay(min, max)) : OptionalLong.empty();
    }
    public static OptionalInt selectRestSpotIndex(AgentRuntimeEntry entry, int mapId, int count) {
        AgentObjectiveVariationSettings settings = settings(entry);
        if (!settings.enabled() || count <= 0) return OptionalInt.empty();
        return OptionalInt.of(settings.restSpotVariationEnabled()
                ? entry.objectiveVariationState().selectRestSpotIndex(mapId, count) : 0);
    }
    public static OptionalInt selectRestFacingDirection(AgentRuntimeEntry entry, int mapId) {
        AgentObjectiveVariationSettings settings = settings(entry);
        if (!settings.enabled()) return OptionalInt.empty();
        return OptionalInt.of(settings.restSpotVariationEnabled()
                ? entry.objectiveVariationState().selectRestFacingDirection(mapId) : 1);
    }
    public static AgentPlanCompletionMode selectPostPlanBehavior(AgentRuntimeEntry entry, int mapId) {
        AgentObjectiveVariationSettings settings = settings(entry);
        return settings.enabled() && settings.restSpotVariationEnabled()
                ? entry.objectiveVariationState().selectPostPlanBehavior(mapId) : AgentPlanCompletionMode.SIT;
    }
    private static long agentId(AgentRuntimeEntry entry) {
        return entry != null && AgentRuntimeIdentityRuntime.hasBot(entry)
                ? AgentRuntimeIdentityRuntime.bot(entry).getId() : 0L;
    }
}
