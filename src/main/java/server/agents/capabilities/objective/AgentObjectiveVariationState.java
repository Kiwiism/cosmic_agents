package server.agents.capabilities.objective;

import server.agents.profiles.AgentBehaviorProfile;

import java.util.SplittableRandom;

/** Mutable decision counters owned by one Agent runtime entry, independent of any plan. */
public final class AgentObjectiveVariationState {
    private static final long NPC_DELAY_DOMAIN = 0x4E50432D44454C59L;
    private static final long OBJECTIVE_DELAY_DOMAIN = 0x4F424A2D44454C59L;
    private static final long NPC_ANCHOR_DOMAIN = 0x4E50432D414E4348L;
    private static final long CASH_SHOP_VISIT_DOMAIN = 0x4341534853484F50L;
    private static final long REST_SPOT_DOMAIN = 0x524553542D53504FL;
    private static final long REST_FACING_DOMAIN = 0x524553542D464143L;
    private static final long POST_PLAN_BEHAVIOR_DOMAIN = 0x504F5354504C414EL;

    private AgentObjectiveVariationSettings settings = AgentObjectiveVariationSettings.disabled();
    private long identitySalt;
    private long npcDelayOrdinal;
    private long objectiveDelayOrdinal;
    private long npcAnchorOrdinal;
    private long cashShopVisitOrdinal;
    private long restSpotOrdinal;
    private long restFacingOrdinal;

    public synchronized void configure(AgentObjectiveVariationSettings configured, long identitySalt) {
        settings = configured == null ? AgentObjectiveVariationSettings.disabled() : configured;
        this.identitySalt = identitySalt;
        npcDelayOrdinal = objectiveDelayOrdinal = npcAnchorOrdinal = cashShopVisitOrdinal = 0L;
        restSpotOrdinal = restFacingOrdinal = 0L;
    }

    public synchronized AgentObjectiveVariationSettings settings() { return settings; }

    synchronized long sampleNpcDelay(AgentBehaviorProfile.DelayRange fallback) {
        return sample(settings.beforeNpcInteractionMs() == null ? fallback : settings.beforeNpcInteractionMs(),
                NPC_DELAY_DOMAIN, npcDelayOrdinal++);
    }

    synchronized long sampleObjectiveDelay(AgentBehaviorProfile.DelayRange fallback) {
        return sample(settings.betweenObjectivesMs() == null ? fallback : settings.betweenObjectivesMs(),
                OBJECTIVE_DELAY_DOMAIN, objectiveDelayOrdinal++);
    }

    synchronized int selectNpcAnchorIndex(int mapId, int npcId, int count) {
        long salt = ((long) mapId << 32) ^ Integer.toUnsignedLong(npcId);
        return new SplittableRandom(decisionSeed(NPC_ANCHOR_DOMAIN ^ salt, npcAnchorOrdinal++)).nextInt(count);
    }

    synchronized long sampleCashShopVisitDelay(long minimumMs, long maximumMs) {
        if (minimumMs < 0L || maximumMs < minimumMs) throw new IllegalArgumentException("Invalid delay range");
        return minimumMs == maximumMs ? minimumMs
                : new SplittableRandom(decisionSeed(CASH_SHOP_VISIT_DOMAIN, cashShopVisitOrdinal++))
                .nextLong(minimumMs, maximumMs + 1L);
    }

    synchronized int selectRestSpotIndex(int mapId, int count) {
        return new SplittableRandom(decisionSeed(REST_SPOT_DOMAIN ^ Integer.toUnsignedLong(mapId), restSpotOrdinal++))
                .nextInt(count);
    }

    synchronized int selectRestFacingDirection(int mapId) {
        return new SplittableRandom(decisionSeed(REST_FACING_DOMAIN ^ Integer.toUnsignedLong(mapId), restFacingOrdinal++))
                .nextBoolean() ? 1 : -1;
    }

    synchronized AgentPlanCompletionMode selectPostPlanBehavior(int mapId) {
        int sample = new SplittableRandom(decisionSeed(
                POST_PLAN_BEHAVIOR_DOMAIN ^ Integer.toUnsignedLong(mapId), 0L)).nextInt(100);
        if (sample < 45) return AgentPlanCompletionMode.SIT;
        if (sample < 70) return AgentPlanCompletionMode.WANDER;
        if (sample < 85) return AgentPlanCompletionMode.FIDGET;
        return AgentPlanCompletionMode.IDLE;
    }

    private long sample(AgentBehaviorProfile.DelayRange range, long domain, long ordinal) {
        if (range == null || range.max() == 0) return 0L;
        return new SplittableRandom(decisionSeed(domain, ordinal)).nextLong(range.min(), (long) range.max() + 1L);
    }

    private long decisionSeed(long domain, long ordinal) {
        long value = settings.seed() ^ identitySalt ^ domain ^ (ordinal * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
