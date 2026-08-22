package server.agents.runtime.activity.control.facade;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AgentLiveActivityFacadeRegistry {
    private final Map<AgentActivityKind, AgentLiveActivityFacadeProvider> providers;

    public AgentLiveActivityFacadeRegistry(
            List<? extends AgentLiveActivityFacadeProvider> providers) {
        if (providers == null) throw new IllegalArgumentException("live facades are required");
        EnumMap<AgentActivityKind, AgentLiveActivityFacadeProvider> indexed =
                new EnumMap<>(AgentActivityKind.class);
        for (AgentLiveActivityFacadeProvider provider : providers) {
            if (provider == null || provider.kind() == null
                    || indexed.putIfAbsent(provider.kind(), provider) != null) {
                throw new IllegalArgumentException("one live facade per activity kind is required");
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public AgentLiveActivityFacade bind(
            AgentActivityKind kind, AgentRuntimeEntry entry, Character agent) {
        AgentLiveActivityFacadeProvider provider = providers.get(kind);
        if (provider == null) throw new IllegalStateException("no live facade for " + kind);
        AgentLiveActivityFacade facade = provider.bind(entry, agent);
        if (facade == null || facade.kind() != kind) {
            throw new IllegalStateException("live facade provider returned mismatched ownership");
        }
        return facade;
    }

    public boolean coversAllPrimaryActivities() {
        return providers.keySet().containsAll(java.util.Set.of(AgentActivityKind.values()));
    }
}
