package server.agents.capabilities.supplies;

import client.inventory.Item;
import server.StatEffect;

import java.util.Collection;
import java.util.function.Function;

public final class AgentAutopotPolicy {
    private AgentAutopotPolicy() {
    }

    public enum PotionTier {
        FLAT_SINGLE,
        FLAT_MIXED,
        RATE_SINGLE,
        RATE_MIXED
    }

    public record PotionRanking(PotionTier tier, double value) {
        public boolean betterThan(PotionRanking other) {
            if (other == null) {
                return true;
            }
            int tierComparison = Integer.compare(this.tier.ordinal(), other.tier.ordinal());
            if (tierComparison != 0) {
                return tierComparison < 0;
            }
            return this.value < other.value;
        }
    }

    public record AutopotChoice(int hpItemId, PotionRanking hpRank, int mpItemId, PotionRanking mpRank) {
    }

    public static PotionRanking classifyForSlot(StatEffect effect, boolean hpSlot) {
        if (effect == null) {
            return null;
        }
        int flatPrimary = Math.max(0, hpSlot ? effect.getHp() : effect.getMp());
        int flatOther = Math.max(0, hpSlot ? effect.getMp() : effect.getHp());
        double ratePrimary = Math.max(0.0, hpSlot ? effect.getHpRate() : effect.getMpRate());
        double rateOther = Math.max(0.0, hpSlot ? effect.getMpRate() : effect.getHpRate());

        if (flatPrimary == 0 && ratePrimary == 0.0) {
            return null;
        }

        boolean hasFlat = flatPrimary > 0 || flatOther > 0;
        boolean hasRate = ratePrimary > 0 || rateOther > 0;

        if (hasFlat && !hasRate) {
            boolean mixed = flatPrimary > 0 && flatOther > 0;
            return new PotionRanking(mixed ? PotionTier.FLAT_MIXED : PotionTier.FLAT_SINGLE, flatPrimary);
        }
        if (hasRate && !hasFlat) {
            boolean mixed = ratePrimary > 0 && rateOther > 0;
            return new PotionRanking(mixed ? PotionTier.RATE_MIXED : PotionTier.RATE_SINGLE, ratePrimary);
        }
        return new PotionRanking(PotionTier.RATE_MIXED, ratePrimary > 0 ? ratePrimary : flatPrimary);
    }

    public static AutopotChoice computeChoice(Collection<Item> items, Function<Integer, StatEffect> effectLookup) {
        int hpItemId = -1;
        int mpItemId = -1;
        PotionRanking bestHp = null;
        PotionRanking bestMp = null;
        for (Item item : items) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (effect == null || !effect.getStatups().isEmpty()) {
                continue;
            }
            PotionRanking hpRank = classifyForSlot(effect, true);
            if (hpRank != null && hpRank.betterThan(bestHp)) {
                bestHp = hpRank;
                hpItemId = item.getItemId();
            }
            PotionRanking mpRank = classifyForSlot(effect, false);
            if (mpRank != null && mpRank.betterThan(bestMp)) {
                bestMp = mpRank;
                mpItemId = item.getItemId();
            }
        }
        return new AutopotChoice(hpItemId, bestHp, mpItemId, bestMp);
    }
}
