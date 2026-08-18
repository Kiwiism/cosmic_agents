package server.agents.field;

import client.Character;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;

import java.io.IOException;

/** Reuses the legal level-25 observation loadout and adds the requested KPQ accuracy reserve. */
public final class AgentKpqTestFixtureService {
    private static final int SNIPER_PILL = 2_002_008;

    private AgentKpqTestFixtureService() {
    }

    public static void prepare(AgentRuntimeEntry entry, long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int appearance = (int) Math.floorMod(seed, MapleIslandCohortCharacterCatalog.COMBINATION_COUNT);
        CosmicMapleIslandCohortIdentity.apply(agent,
                MapleIslandCohortCharacterCatalog.template(appearance));
        AgentFieldObservationFixtureService.prepare(entry, 25, seed, nowMs);
        if (!AgentInventoryGatewayRuntime.inventory().addItem(agent, SNIPER_PILL, (short) 100)) {
            throw new IllegalStateException("Could not add KPQ accuracy pills");
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().useItem(agent, SNIPER_PILL);
    }
}
