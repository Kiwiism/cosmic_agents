package server.agents.capabilities.inventory;

import server.StatEffect;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.function.IntFunction;

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

    public static StatEffect itemEffect(int itemId) {
        return itemEffect(itemId, CosmicAgentServerAdapter.INSTANCE.inventory()::getItemEffect);
    }

    static StatEffect itemEffect(int itemId, IntFunction<StatEffect> lookup) {
        try {
            return lookup.apply(itemId);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isRecoveryPotion(int itemId) {
        return isRecoveryPotion(itemEffect(itemId));
    }

    public static boolean isBuffConsumable(int itemId) {
        return isBuffConsumable(itemEffect(itemId));
    }
}
