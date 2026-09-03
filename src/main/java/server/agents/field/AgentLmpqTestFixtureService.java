package server.agents.field;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Deterministic level-60 builds used only by the GM LMPQ observation harness. */
public final class AgentLmpqTestFixtureService {
    public static final int LMPQ_LEVEL = 60;
    private static final List<String> BUILD_IDS = List.of(
            "spearman-spear", "cleric-wand", "hunter-bow",
            "bandit-dagger", "gunslinger-gun", "il-wizard-staff");
    private static final Set<Integer> LMPQ_MOBS = Set.of(
            9_400_209, 9_400_210, 9_400_211, 9_400_212, 9_400_213,
            9_400_214, 9_400_215, 9_400_216, 9_400_217, 9_400_218);

    private AgentLmpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, int ordinal,
                                            long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned LMPQ Agent is required");
        String buildId = BUILD_IDS.get(Math.floorMod(ordinal, BUILD_IDS.size()));
        AgentBalrogTestFixtureService.Build build = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing LMPQ build " + buildId));
        AgentLpqTestFixtureService.applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, LMPQ_LEVEL, LMPQ_MOBS, nowMs);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("LMPQ fixture has unspent AP/SP: ap="
                    + prepared.remainingAp() + ", sp="
                    + java.util.Arrays.toString(prepared.remainingSps()));
        }
        return new PreparationResult(prepared.level(), prepared.career(), build.job().name(),
                prepared.weaponItemId(), prepared.weaponAttack(), prepared.minimumHitChance());
    }

    public record PreparationResult(int level, String career, String job,
                                    int weaponItemId, int weaponAttack,
                                    double minimumHitChance) { }
}
