package server.agents.field;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;

/** Legal level-25 observation loadout for dedicated HPQ test Agents. */
public final class AgentHpqTestFixtureService {
    private static final int HPQ_START_LEVEL = config.AgentTuning.intValue(
            "server.agents.field.AgentHpqTestFixtureService.HPQ_START_LEVEL");

    private AgentHpqTestFixtureService() {
    }

    public static PreparationResult prepare(AgentRuntimeEntry entry, long seed, long nowMs)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned HPQ Agent is required");
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForKpq(
                        entry, HPQ_START_LEVEL, seed, nowMs);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("HPQ fixture left unspent AP/SP for " + prepared.name());
        }
        return new PreparationResult(prepared.level(), prepared.career(), prepared.completeBuild(),
                prepared.remainingAp(), prepared.remainingSps(), prepared.weaponItemId(),
                prepared.weaponAttack());
    }

    public record PreparationResult(int level, String career, boolean completeBuild,
                                    int remainingAp, int[] remainingSps,
                                    int weaponItemId, int weaponAttack) {
        public PreparationResult {
            remainingSps = remainingSps == null ? new int[0] : remainingSps.clone();
        }

        @Override public int[] remainingSps() { return remainingSps.clone(); }
    }
}
