package server.agents.capabilities.inventory;

import server.StatEffect;

public final class AgentUseItemClassificationPolicy {
    private AgentUseItemClassificationPolicy() {
    }

    public static boolean isRecoveryPotion(StatEffect effect) {
        if (effect == null) {
            return false;
        }
        boolean heals = effect.getHp() > 0
                || effect.getMp() > 0
                || effect.getHpRate() > 0
                || effect.getMpRate() > 0;
        return heals && effect.getStatups().isEmpty();
    }

    public static boolean isBuffConsumable(StatEffect effect) {
        return effect != null && !effect.getStatups().isEmpty();
    }
}
