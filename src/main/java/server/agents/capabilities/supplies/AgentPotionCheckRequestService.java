package server.agents.capabilities.supplies;

import client.Character;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeHandle;

public final class AgentPotionCheckRequestService {
    private AgentPotionCheckRequestService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(EntryResolver<E> entryResolver,
                                                      PotionCheckRequester<E> potionCheckRequester) {
    }

    @FunctionalInterface
    public interface EntryResolver<E extends AgentRuntimeHandle> {
        E resolve(Character agent);
    }

    @FunctionalInterface
    public interface PotionCheckRequester<E extends AgentRuntimeHandle> {
        void requestPotionCheckSoon(E entry, int soonDelayMs);
    }

    public static <E extends AgentRuntimeHandle> void requestPotionCheckSoon(Character agent, Hooks<E> hooks) {
        if (agent == null) {
            return;
        }
        E entry = hooks.entryResolver().resolve(agent);
        if (entry == null) {
            return;
        }
        int soonDelayMs = Math.max(0, AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS);
        hooks.potionCheckRequester().requestPotionCheckSoon(entry, soonDelayMs);
    }
}
