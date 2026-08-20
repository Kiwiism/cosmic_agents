package server.agents.field;

import client.BuffStat;
import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;

import java.io.IOException;
import java.util.Set;

/** Reuses the legal observation loadout pipeline with KPQ-specific accuracy calibration. */
public final class AgentKpqTestFixtureService {
    private static final int SNIPER_PILL_ITEM_ID = 2_002_008;
    private static final Set<Integer> KPQ_FIRST_MAP_MOBS = Set.of(9300001);
    private static final int KPQ_START_LEVEL = config.AgentTuning.intValue(
            "server.agents.field.AgentKpqTestFixtureService.KPQ_START_LEVEL");

    private AgentKpqTestFixtureService() {
    }

    public static PreparationResult prepare(AgentRuntimeEntry entry, long seed, long nowMs)
            throws IOException {
        return prepare(entry, null, seed, nowMs);
    }

    public static PreparationResult prepare(
            AgentRuntimeEntry entry, String requestedCareer, long seed, long nowMs)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int appearance = (int) Math.floorMod(seed, MapleIslandCohortCharacterCatalog.COMBINATION_COUNT);
        CosmicMapleIslandCohortIdentity.apply(agent,
                MapleIslandCohortCharacterCatalog.template(appearance));
        AgentFieldObservationFixtureService.Prepared prepared = requestedCareer == null
                ? AgentFieldObservationFixtureService.prepareForKpq(
                        entry, KPQ_START_LEVEL, KPQ_FIRST_MAP_MOBS, seed, nowMs)
                : AgentFieldObservationFixtureService.prepareForKpq(
                        entry, KPQ_START_LEVEL, KPQ_FIRST_MAP_MOBS, requestedCareer, seed, nowMs);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("KPQ fixture left unspent AP/SP for " + prepared.name());
        }
        return new PreparationResult(prepared.level(), prepared.minimumHitChance(), prepared.career(),
                prepared.completeBuild(),
                prepared.remainingAp(), prepared.remainingSps(),
                prepared.weaponItemId(), prepared.weaponAttack());
    }

    /** Keeps the ordinary accuracy consumable active during long observation runs. */
    public static boolean ensureAccuracyPillActive(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) return false;
        if (agent.getBuffedValue(BuffStat.ACC) != null) return true;
        return AgentPrimitiveCapabilityGatewayRuntime.gateway().useItem(agent, SNIPER_PILL_ITEM_ID);
    }

    public record PreparationResult(int level, double minimumHitChance, String career, boolean completeBuild,
                                   int remainingAp, int[] remainingSps,
                                   int weaponItemId, int weaponAttack) {
    }
}
