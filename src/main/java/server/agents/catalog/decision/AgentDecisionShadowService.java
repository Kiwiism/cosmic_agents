package server.agents.catalog.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Evaluates catalog recommendations beside live behavior without changing execution state. */
public final class AgentDecisionShadowService {
    private static final Logger log = LoggerFactory.getLogger(AgentDecisionShadowService.class);

    private final AgentDecisionCatalogRuntimeConfig config;
    private final AgentTopologyQueryService topology;
    private final AgentCombatPolicyQueryService combat;
    private final LongAdder navigationObservations = new LongAdder();
    private final LongAdder navigationMissing = new LongAdder();
    private final LongAdder navigationAgreement = new LongAdder();
    private final LongAdder navigationMismatch = new LongAdder();
    private final LongAdder combatObservations = new LongAdder();
    private final LongAdder combatMissing = new LongAdder();
    private final LongAdder combatMobRecognized = new LongAdder();
    private final LongAdder combatAnchorMatched = new LongAdder();
    private final AtomicLong nextLogAtMs = new AtomicLong();

    public AgentDecisionShadowService(AgentDecisionCatalogRuntimeConfig config,
                                      AgentTopologyQueryService topology,
                                      AgentCombatPolicyQueryService combat) {
        if (config == null || topology == null || combat == null) {
            throw new IllegalArgumentException("Decision shadow config and query services are required");
        }
        this.config = config;
        this.topology = topology;
        this.combat = combat;
        this.nextLogAtMs.set(System.currentTimeMillis() + config.logIntervalMs());
    }

    public void observeNavigation(AgentRuntimeEntry entry,
                                  int mapId,
                                  int sourceX,
                                  int sourceY,
                                  int targetX,
                                  int targetY,
                                  int liveSourceRegionId,
                                  int liveTargetRegionId,
                                  long nowMs) {
        if (!config.navigationShadowEnabled() || entry == null
                || !sampling(entry).allowNavigation(nowMs, config.sampleIntervalMs())) {
            return;
        }
        navigationObservations.increment();
        AgentTopologyQueryService.NavigationRecommendation recommendation = topology.recommend(
                mapId, sourceX, sourceY, targetX, targetY).orElse(null);
        if (recommendation == null) {
            navigationMissing.increment();
            maybeLog(nowMs);
            return;
        }
        if (liveSourceRegionId >= 0 && liveTargetRegionId >= 0
                && recommendation.sameComponent() == (liveSourceRegionId == liveTargetRegionId)) {
            navigationAgreement.increment();
        } else {
            navigationMismatch.increment();
        }
        maybeLog(nowMs);
    }

    public void observeCombatTarget(AgentRuntimeEntry entry,
                                    int mapId,
                                    int agentX,
                                    int agentY,
                                    int mobId,
                                    int targetX,
                                    int targetY,
                                    long nowMs) {
        if (!config.combatShadowEnabled() || entry == null
                || !sampling(entry).allowCombat(nowMs, config.sampleIntervalMs())) {
            return;
        }
        combatObservations.increment();
        AgentCombatPolicyQueryService.TargetRecommendation recommendation = combat.recommendTarget(
                mapId, agentX, agentY, mobId, targetX, targetY).orElse(null);
        if (recommendation == null) {
            combatMissing.increment();
            maybeLog(nowMs);
            return;
        }
        if (recommendation.mobRecognized()) {
            combatMobRecognized.increment();
        }
        if (recommendation.targetOnRecommendedAnchor()) {
            combatAnchorMatched.increment();
        }
        maybeLog(nowMs);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                navigationObservations.sum(),
                navigationMissing.sum(),
                navigationAgreement.sum(),
                navigationMismatch.sum(),
                combatObservations.sum(),
                combatMissing.sum(),
                combatMobRecognized.sum(),
                combatAnchorMatched.sum());
    }

    private AgentDecisionShadowSamplingState sampling(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentDecisionShadowSamplingState.STATE_KEY);
    }

    private void maybeLog(long nowMs) {
        long dueAt = nextLogAtMs.get();
        if (nowMs < dueAt || !nextLogAtMs.compareAndSet(dueAt, nowMs + config.logIntervalMs())) {
            return;
        }
        Snapshot snapshot = snapshot();
        log.info("Agent decision catalog shadow: nav={} missing={} agreement={} mismatch={} "
                        + "combat={} missing={} mobRecognized={} anchorMatched={}",
                snapshot.navigationObservations(),
                snapshot.navigationMissing(),
                snapshot.navigationAgreement(),
                snapshot.navigationMismatch(),
                snapshot.combatObservations(),
                snapshot.combatMissing(),
                snapshot.combatMobRecognized(),
                snapshot.combatAnchorMatched());
    }

    public record Snapshot(long navigationObservations,
                           long navigationMissing,
                           long navigationAgreement,
                           long navigationMismatch,
                           long combatObservations,
                           long combatMissing,
                           long combatMobRecognized,
                           long combatAnchorMatched) {
        public static Snapshot empty() {
            return new Snapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }
}
