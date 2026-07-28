package server.agents.capabilities.supplies;

import client.inventory.Item;
import server.StatEffect;
import server.agents.capabilities.supplies.AgentPotionRecoveryPolicy.Recovery;

import java.util.Collection;
import java.util.function.Function;

/**
 * Chooses the potion whose normalized recovery best matches the deficit at
 * the moment it will be consumed.
 */
public final class AgentAutopotPolicy {
    private static final int IDEAL_MIN_COVERAGE_BPS = config.AgentTuning.intValue(
            "server.agents.capabilities.supplies.AgentAutopotPolicy.IDEAL_MIN_COVERAGE_BPS");
    private static final int IDEAL_MAX_COVERAGE_BPS = config.AgentTuning.intValue(
            "server.agents.capabilities.supplies.AgentAutopotPolicy.IDEAL_MAX_COVERAGE_BPS");

    private AgentAutopotPolicy() {
    }

    public record PotionRanking(
            int coverageBand,
            int primaryRecovery,
            int secondaryRecovery,
            int coverageBasisPoints,
            boolean mixed,
            boolean percentageBased) {
    }

    public record AutopotItemChoice(int itemId, short position, PotionRanking ranking) {
    }

    public record AutopotChoice(AutopotItemChoice hp, AutopotItemChoice mp) {
        public int hpItemId() {
            return hp == null ? -1 : hp.itemId();
        }

        public int mpItemId() {
            return mp == null ? -1 : mp.itemId();
        }

        public PotionRanking hpRank() {
            return hp == null ? null : hp.ranking();
        }

        public PotionRanking mpRank() {
            return mp == null ? null : mp.ranking();
        }
    }

    public static AutopotChoice computeChoice(
            Collection<Item> items,
            Function<Integer, StatEffect> effectLookup,
            int maxHp,
            int maxMp,
            int hpDeficit,
            int mpDeficit) {
        return new AutopotChoice(
                select(items, effectLookup, maxHp, maxMp, hpDeficit, true),
                select(items, effectLookup, maxHp, maxMp, mpDeficit, false));
    }

    public static AutopotItemChoice select(
            Collection<Item> items,
            Function<Integer, StatEffect> effectLookup,
            int maxHp,
            int maxMp,
            int deficit,
            boolean forHp) {
        AutopotItemChoice best = null;
        int target = Math.max(1, deficit);
        for (Item item : items) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (effect == null || !effect.getStatups().isEmpty()) {
                continue;
            }
            Recovery recovery =
                    AgentPotionRecoveryPolicy.recovery(effect, maxHp, maxMp, forHp);
            if (recovery == null) {
                continue;
            }
            int coverage = AgentPotionRecoveryPolicy.coverageBasisPoints(
                    recovery.primary(), target);
            PotionRanking ranking = new PotionRanking(
                    band(coverage),
                    recovery.primary(),
                    recovery.secondary(),
                    coverage,
                    recovery.mixed(),
                    recovery.percentageBased());
            AutopotItemChoice candidate =
                    new AutopotItemChoice(item.getItemId(), item.getPosition(), ranking);
            if (better(candidate, best, target)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(
            AutopotItemChoice candidate, AutopotItemChoice current, int targetDeficit) {
        if (current == null) {
            return true;
        }
        PotionRanking left = candidate.ranking();
        PotionRanking right = current.ranking();
        if (left.coverageBand() != right.coverageBand()) {
            return left.coverageBand() < right.coverageBand();
        }
        if (left.mixed() != right.mixed()) {
            return !left.mixed();
        }
        if (left.coverageBand() == 1
                && left.primaryRecovery() != right.primaryRecovery()) {
            return left.primaryRecovery() > right.primaryRecovery();
        }
        if (left.coverageBand() == 2
                && left.primaryRecovery() != right.primaryRecovery()) {
            return left.primaryRecovery() < right.primaryRecovery();
        }
        int leftWaste = Math.abs(left.primaryRecovery() - targetDeficit);
        int rightWaste = Math.abs(right.primaryRecovery() - targetDeficit);
        if (leftWaste != rightWaste) {
            return leftWaste < rightWaste;
        }
        return candidate.itemId() < current.itemId();
    }

    private static int band(int coverageBasisPoints) {
        if (coverageBasisPoints >= IDEAL_MIN_COVERAGE_BPS
                && coverageBasisPoints <= IDEAL_MAX_COVERAGE_BPS) {
            return 0;
        }
        return coverageBasisPoints < IDEAL_MIN_COVERAGE_BPS ? 1 : 2;
    }
}
