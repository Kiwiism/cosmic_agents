package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Mutable per-Agent counters for replayable combat variation decisions. */
public final class AgentCombatVariationState {
    public static final AgentCapabilityStateKey<AgentCombatVariationState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "combat-variation", AgentCombatVariationState.class, AgentCombatVariationState::new);

    private static final long ANCHOR_ROLE_DOMAIN = 0x414E43484F522D52L;
    private static final long TARGET_STYLE_DOMAIN = 0x5441524745542D53L;
    private static final long TARGET_RANK_DOMAIN = 0x5441524745542D52L;

    private AgentCombatVariationSettings settings = AgentCombatVariationSettings.disabled();
    private long targetDecisionSequence;
    private int automaticAnchorMobId = -1;
    private int automaticAnchorMapId = -1;
    private int automaticAnchorRegionId = -1;

    synchronized void configure(AgentCombatVariationSettings configured) {
        settings = configured == null ? AgentCombatVariationSettings.disabled() : configured;
        targetDecisionSequence = 0L;
        clearAutomaticAnchor();
    }

    synchronized AgentCombatVariationSettings settings() {
        return settings;
    }

    synchronized boolean platformAnchorRole() {
        return settings.platformAnchorEnabled()
                && unitDouble(decisionSeed(ANCHOR_ROLE_DOMAIN, 0L))
                < settings.platformAnchorProbability();
    }

    synchronized int selectTargetIndex(int candidateCount) {
        int shortlistSize = Math.min(Math.max(0, candidateCount), settings.targetShortlistLimit());
        if (!settings.targetSelectionVariationEnabled() || shortlistSize < 3) {
            return 0;
        }

        long sequence = targetDecisionSequence++;
        boolean anchorRole = platformAnchorRole();
        if (!anchorRole && unitDouble(decisionSeed(TARGET_STYLE_DOMAIN, sequence))
                >= settings.middleTargetProbability()) {
            return 0;
        }

        int firstMiddleIndex = Math.max(1, shortlistSize / 3);
        int middleEndExclusive = Math.max(firstMiddleIndex + 1, (shortlistSize * 2 + 2) / 3);
        middleEndExclusive = Math.min(shortlistSize, middleEndExclusive);
        int width = middleEndExclusive - firstMiddleIndex;
        return firstMiddleIndex + (int) Long.remainderUnsigned(
                decisionSeed(TARGET_RANK_DOMAIN, sequence), width);
    }

    synchronized void markAutomaticAnchor(int mobId, int mapId, int regionId) {
        automaticAnchorMobId = mobId;
        automaticAnchorMapId = mapId;
        automaticAnchorRegionId = regionId;
    }

    synchronized int automaticAnchorMobId() {
        return automaticAnchorMobId;
    }

    synchronized int automaticAnchorMapId() {
        return automaticAnchorMapId;
    }

    synchronized int automaticAnchorRegionId() {
        return automaticAnchorRegionId;
    }

    synchronized void clearAutomaticAnchor() {
        automaticAnchorMobId = -1;
        automaticAnchorMapId = -1;
        automaticAnchorRegionId = -1;
    }

    private long decisionSeed(long domain, long ordinal) {
        return mix(settings.seed() ^ domain ^ (ordinal * 0x9E3779B97F4A7C15L));
    }

    private static double unitDouble(long seed) {
        return (mix(seed) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
