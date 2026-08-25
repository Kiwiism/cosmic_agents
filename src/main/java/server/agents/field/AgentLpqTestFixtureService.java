package server.agents.field;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Legal level-45 LPQ observation builds with deterministic role coverage. */
public final class AgentLpqTestFixtureService {
    private static final int LPQ_START_LEVEL = config.AgentTuning.intValue(
            "server.agents.field.AgentLpqTestFixtureService.LPQ_START_LEVEL");
    private static final List<String> BUILD_IDS = List.of(
            "cleric-wand", "assassin-claw", "hunter-bow", "fighter-1h-sword",
            "gunslinger-gun", "bandit-dagger");
    private static final Set<Integer> LPQ_COMBAT_MOBS = Set.of(
            9_300_006, 9_300_007, 9_300_008, 9_300_010, 9_300_012, 9_300_014);

    private AgentLpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, int ordinal, long seed, long nowMs)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned LPQ Agent is required");
        String buildId = BUILD_IDS.get(Math.floorMod(ordinal, BUILD_IDS.size()));
        AgentBalrogTestFixtureService.Build build = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing LPQ fixture build " + buildId));
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, LPQ_START_LEVEL, LPQ_COMBAT_MOBS, nowMs);
        if (!prepared.completeBuild()) throw new IllegalStateException("LPQ fixture has unspent AP/SP");
        return new PreparationResult(prepared.level(), prepared.career(),
                prepared.weaponItemId(), prepared.weaponAttack());
    }

    public record PreparationResult(int level, String career, int weaponItemId, int weaponAttack) { }
}
