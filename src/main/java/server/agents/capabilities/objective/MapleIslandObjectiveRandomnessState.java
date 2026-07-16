package server.agents.capabilities.objective;

import server.agents.profiles.AgentBehaviorProfile;

import java.util.SplittableRandom;

/** Mutable decision counters owned by one Agent runtime entry. */
public final class MapleIslandObjectiveRandomnessState {
    private static final long NPC_DELAY_DOMAIN = 0x4E50432D44454C59L;
    private static final long OBJECTIVE_DELAY_DOMAIN = 0x4F424A2D44454C59L;
    private static final long NPC_ANCHOR_DOMAIN = 0x4E50432D414E4348L;
    private static final long CASH_SHOP_VISIT_DOMAIN = 0x4341534853484F50L;
    private static final long REST_SPOT_DOMAIN = 0x524553542D53504FL;
    private static final long REST_FACING_DOMAIN = 0x524553542D464143L;

    private MapleIslandObjectiveRandomnessSettings settings =
            MapleIslandObjectiveRandomnessSettings.disabled();
    private long identitySalt;
    private long npcDelayOrdinal;
    private long objectiveDelayOrdinal;
    private long npcAnchorOrdinal;
    private long cashShopVisitOrdinal;
    private long restSpotOrdinal;
    private long restFacingOrdinal;

    public synchronized void configure(MapleIslandObjectiveRandomnessSettings settings) {
        configure(settings, 0L);
    }

    synchronized void configure(MapleIslandObjectiveRandomnessSettings settings, long identitySalt) {
        this.settings = settings == null
                ? MapleIslandObjectiveRandomnessSettings.disabled() : settings;
        this.identitySalt = identitySalt;
        npcDelayOrdinal = 0L;
        objectiveDelayOrdinal = 0L;
        npcAnchorOrdinal = 0L;
        cashShopVisitOrdinal = 0L;
        restSpotOrdinal = 0L;
        restFacingOrdinal = 0L;
    }

    public synchronized MapleIslandObjectiveRandomnessSettings settings() {
        return settings;
    }

    synchronized long sampleNpcDelay(AgentBehaviorProfile.DelayRange fallback) {
        AgentBehaviorProfile.DelayRange range = settings.beforeNpcInteractionMs() == null
                ? fallback : settings.beforeNpcInteractionMs();
        return sample(range, NPC_DELAY_DOMAIN, npcDelayOrdinal++);
    }

    synchronized long sampleObjectiveDelay(AgentBehaviorProfile.DelayRange fallback) {
        AgentBehaviorProfile.DelayRange range = settings.betweenObjectivesMs() == null
                ? fallback : settings.betweenObjectivesMs();
        return sample(range, OBJECTIVE_DELAY_DOMAIN, objectiveDelayOrdinal++);
    }

    synchronized int selectNpcAnchorIndex(int mapId, int npcId, int candidateCount) {
        long salt = ((long) mapId << 32) ^ Integer.toUnsignedLong(npcId);
        long decisionSeed = decisionSeed(NPC_ANCHOR_DOMAIN ^ salt, npcAnchorOrdinal++);
        return new SplittableRandom(decisionSeed).nextInt(candidateCount);
    }

    synchronized long sampleCashShopVisitDelay(long minimumMs, long maximumMs) {
        if (minimumMs < 0L || maximumMs < minimumMs) {
            throw new IllegalArgumentException("Invalid Cash Shop visit delay range");
        }
        if (minimumMs == maximumMs) {
            return minimumMs;
        }
        return new SplittableRandom(decisionSeed(CASH_SHOP_VISIT_DOMAIN, cashShopVisitOrdinal++))
                .nextLong(minimumMs, maximumMs + 1L);
    }

    synchronized int selectRestSpotIndex(int mapId, int candidateCount) {
        long decisionSeed = decisionSeed(
                REST_SPOT_DOMAIN ^ Integer.toUnsignedLong(mapId), restSpotOrdinal++);
        return new SplittableRandom(decisionSeed).nextInt(candidateCount);
    }

    synchronized int selectRestFacingDirection(int mapId) {
        long decisionSeed = decisionSeed(
                REST_FACING_DOMAIN ^ Integer.toUnsignedLong(mapId), restFacingOrdinal++);
        return new SplittableRandom(decisionSeed).nextBoolean() ? 1 : -1;
    }

    private long sample(AgentBehaviorProfile.DelayRange range, long domain, long ordinal) {
        if (range == null || range.max() == 0) {
            return 0L;
        }
        return new SplittableRandom(decisionSeed(domain, ordinal))
                .nextLong(range.min(), (long) range.max() + 1L);
    }

    private long decisionSeed(long domain, long ordinal) {
        long value = settings.seed() ^ identitySalt ^ domain ^ (ordinal * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
