package server.agents.progression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Shared mutation boundary for durable Yeti and Pepe-scroll campaign outcomes. */
public final class AgentMushroomKingdomFarmProgressRuntime {
    public static final int MAX_YETI_RUNS = 10;
    public static final long YETI_COOLDOWN_MS = 24L * 60L * 60L * 1_000L;
    private static final Logger log = LoggerFactory.getLogger(
            AgentMushroomKingdomFarmProgressRuntime.class);
    private static final FileAgentMushroomKingdomFarmProgressStore STORE =
            FileAgentMushroomKingdomFarmProgressStore.runtimeDefault();

    private AgentMushroomKingdomFarmProgressRuntime() { }

    public static synchronized AgentMushroomKingdomFarmProgress load(int characterId, long nowMs) {
        try {
            return STORE.load(characterId).orElseGet(
                    () -> AgentMushroomKingdomFarmProgress.empty(characterId, nowMs));
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not load Mushroom Kingdom farm progress for {}", characterId, failure);
            return AgentMushroomKingdomFarmProgress.empty(characterId, nowMs);
        }
    }

    public static synchronized AgentMushroomKingdomFarmProgress beginYetiCampaign(
            int characterId, long nowMs) {
        AgentMushroomKingdomFarmProgress current = load(characterId, nowMs);
        AgentMushroomKingdomFarmProgress reset =
                AgentMushroomKingdomFarmProgressPolicy.beginYetiCampaign(current, nowMs);
        if (reset == current) return current;
        save(reset);
        return reset;
    }

    public static synchronized AgentMushroomKingdomFarmProgress recordYetiRun(
            int characterId, boolean relevantBoxOpportunity, int acquiredWeaponItemId, long nowMs) {
        AgentMushroomKingdomFarmProgress current = load(characterId, nowMs);
        AgentMushroomKingdomFarmProgress updated =
                AgentMushroomKingdomFarmProgressPolicy.recordYetiRun(
                        current, relevantBoxOpportunity, acquiredWeaponItemId, nowMs);
        save(updated);
        return updated;
    }

    public static synchronized AgentMushroomKingdomFarmProgress recordScrollAttempt(
            int characterId, boolean applied, String outcome, long nowMs) {
        AgentMushroomKingdomFarmProgress current = load(characterId, nowMs);
        AgentMushroomKingdomFarmProgress updated =
                AgentMushroomKingdomFarmProgressPolicy.recordScrollAttempt(
                        current, applied, outcome, nowMs);
        save(updated);
        return updated;
    }

    public static synchronized AgentMushroomKingdomFarmProgress recordStopReason(
            int characterId, String reason, long nowMs) {
        AgentMushroomKingdomFarmProgress current = load(characterId, nowMs);
        AgentMushroomKingdomFarmProgress updated =
                AgentMushroomKingdomFarmProgressPolicy.recordStopReason(current, reason, nowMs);
        save(updated);
        return updated;
    }

    public static AgentMushroomKingdomFarmSnapshot snapshot(int characterId, long nowMs) {
        return load(characterId, nowMs).snapshot();
    }

    private static void save(AgentMushroomKingdomFarmProgress value) {
        try {
            STORE.save(value);
        } catch (IOException failure) {
            log.warn("Could not persist Mushroom Kingdom farm progress for {}",
                    value.characterId(), failure);
        }
    }
}
