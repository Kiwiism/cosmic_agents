package server.agents.capabilities.supplies;

import server.StatEffect;

public final class AgentPotionSharePolicy {
    private AgentPotionSharePolicy() {
    }

    public static boolean canShareForSlot(StatEffect effect, boolean forHp) {
        if (effect == null) {
            return false;
        }
        if (forHp) {
            return effect.getHp() != 0 || effect.getHpRate() != 0;
        }
        return effect.getMp() != 0 || effect.getMpRate() != 0;
    }

    public static int recoveryScore(StatEffect effect, boolean forHp) {
        if (effect == null) {
            return Integer.MAX_VALUE;
        }
        if (forHp) {
            if (effect.getHpRate() > 0) {
                return 1_000_000 + (int) (effect.getHpRate() * 1000);
            }
            return effect.getHp();
        }
        if (effect.getMpRate() > 0) {
            return 1_000_000 + (int) (effect.getMpRate() * 1000);
        }
        return effect.getMp();
    }
}
