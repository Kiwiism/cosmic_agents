package server.agents.capabilities.supplies;

import server.StatEffect;

/**
 * Pure normalization shared by potion purchasing, inventory capacity checks,
 * and live autopot selection.
 */
public final class AgentPotionRecoveryPolicy {
    private static final int MIXED_SECONDARY_VALUE_BPS = config.AgentTuning.intValue(
            "server.agents.capabilities.supplies.AgentPotionRecoveryPolicy.MIXED_SECONDARY_VALUE_BPS");

    private AgentPotionRecoveryPolicy() {
    }

    public record Recovery(int primary, int secondary, boolean mixed, boolean percentageBased) {
        public int weightedRecovery() {
            long weighted = primary + (long) secondary * MIXED_SECONDARY_VALUE_BPS / 10_000L;
            return (int) Math.min(Integer.MAX_VALUE, weighted);
        }
    }

    public static Recovery recovery(
            StatEffect effect, int maxHp, int maxMp, boolean forHp) {
        if (effect == null) {
            return null;
        }
        int hp = normalized(effect.getHp(), effect.getHpRate(), maxHp);
        int mp = normalized(effect.getMp(), effect.getMpRate(), maxMp);
        int primary = forHp ? hp : mp;
        int secondary = forHp ? mp : hp;
        if (primary <= 0) {
            return null;
        }
        boolean percentageBased = forHp
                ? effect.getHpRate() > 0.0
                : effect.getMpRate() > 0.0;
        return new Recovery(primary, secondary, secondary > 0, percentageBased);
    }

    public static int coverageBasisPoints(int recovery, int targetDeficit) {
        if (recovery <= 0 || targetDeficit <= 0) {
            return 0;
        }
        return (int) Math.min(
                Integer.MAX_VALUE,
                (long) recovery * 10_000L / targetDeficit);
    }

    private static int normalized(int flat, double rate, int maximum) {
        long rateRecovery = Math.round(Math.max(0.0, rate) * Math.max(0, maximum));
        long total = Math.max(0, flat) + rateRecovery;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
