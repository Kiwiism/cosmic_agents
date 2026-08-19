package server.agents.field;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;

import java.io.IOException;
import java.util.Set;

/** Reuses the legal observation loadout pipeline with KPQ-specific accuracy calibration. */
public final class AgentKpqTestFixtureService {
    private static final Set<Integer> KPQ_FIRST_MAP_MOBS = Set.of(9300000, 9300001);
    private static final int KPQ_START_LEVEL = 25;

    private AgentKpqTestFixtureService() {
    }

    public static PreparationResult prepare(AgentRuntimeEntry entry, long seed, long nowMs)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int appearance = (int) Math.floorMod(seed, MapleIslandCohortCharacterCatalog.COMBINATION_COUNT);
        CosmicMapleIslandCohortIdentity.apply(agent,
                MapleIslandCohortCharacterCatalog.template(appearance));
        AgentFieldObservationFixtureService.Prepared prepared = AgentFieldObservationFixtureService.prepareForKpq(
                entry, KPQ_START_LEVEL, KPQ_FIRST_MAP_MOBS, seed, nowMs);
        return new PreparationResult(prepared.level(), prepared.minimumHitChance());
    }

    public record PreparationResult(int level, double minimumHitChance) {
    }
}
