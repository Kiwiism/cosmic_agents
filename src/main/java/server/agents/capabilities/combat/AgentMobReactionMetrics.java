package server.agents.capabilities.combat;

import java.util.concurrent.atomic.LongAdder;

public final class AgentMobReactionMetrics {
    private static final LongAdder acceptedHits = new LongAdder();
    private static final LongAdder hurtReactions = new LongAdder();
    private static final LongAdder thresholdMet = new LongAdder();
    private static final LongAdder knockbackPrepared = new LongAdder();
    private static final LongAdder knockbackSuppressedBelowThreshold = new LongAdder();
    private static final LongAdder knockbackSuppressedImmobile = new LongAdder();
    private static final LongAdder noObserverSkips = new LongAdder();
    private static final LongAdder targetChanges = new LongAdder();
    private static final LongAdder staleTargets = new LongAdder();
    private static final LongAdder controllerFailures = new LongAdder();
    private static final LongAdder duplicateDamageProtections = new LongAdder();

    private AgentMobReactionMetrics() {
    }

    public static void acceptedHit() { acceptedHits.increment(); }
    public static void hurtReaction() { hurtReactions.increment(); }
    public static void thresholdMet() { thresholdMet.increment(); }
    public static void knockbackPrepared() { knockbackPrepared.increment(); }
    public static void knockbackSuppressedBelowThreshold() { knockbackSuppressedBelowThreshold.increment(); }
    public static void knockbackSuppressedImmobile() { knockbackSuppressedImmobile.increment(); }
    public static void noObserverSkip() { noObserverSkips.increment(); }
    public static void targetChange() { targetChanges.increment(); }
    public static void staleTarget() { staleTargets.increment(); }
    public static void controllerFailure() { controllerFailures.increment(); }
    public static void duplicateDamageProtection() { duplicateDamageProtections.increment(); }

    public static Snapshot snapshot() {
        return new Snapshot(acceptedHits.sum(), hurtReactions.sum(), thresholdMet.sum(),
                knockbackPrepared.sum(), knockbackSuppressedBelowThreshold.sum(),
                knockbackSuppressedImmobile.sum(), noObserverSkips.sum(), targetChanges.sum(),
                staleTargets.sum(), controllerFailures.sum(), duplicateDamageProtections.sum());
    }

    public record Snapshot(long acceptedHits, long hurtReactions, long thresholdMet,
                           long knockbackPrepared, long knockbackSuppressedBelowThreshold,
                           long knockbackSuppressedImmobile, long noObserverSkips, long targetChanges,
                           long staleTargets, long controllerFailures,
                           long duplicateDamageProtections) {
        public String summary() {
            return "accepted=" + acceptedHits + " hurt=" + hurtReactions
                    + " threshold=" + thresholdMet + " knockbackPrepared=" + knockbackPrepared
                    + " knockbackBelowThreshold=" + knockbackSuppressedBelowThreshold
                    + " knockbackImmobile=" + knockbackSuppressedImmobile
                    + " noObserver=" + noObserverSkips + " targets=" + targetChanges
                    + " stale=" + staleTargets + " controllerFailures=" + controllerFailures
                    + " duplicateDamage=" + duplicateDamageProtections;
        }
    }
}
