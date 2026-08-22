package server.agents.runtime.activity.control.binding;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dispatches through registered system providers; it contains no activity-specific branch. */
public final class AgentWorldActivityBindingFactory {
    private final Map<AgentActivityKind, AgentWorldActivityBindingProvider> providers;

    public AgentWorldActivityBindingFactory(
            List<? extends AgentWorldActivityBindingProvider> providers) {
        if (providers == null) throw new IllegalArgumentException("binding providers are required");
        EnumMap<AgentActivityKind, AgentWorldActivityBindingProvider> indexed =
                new EnumMap<>(AgentActivityKind.class);
        for (AgentWorldActivityBindingProvider provider : providers) {
            if (provider == null || provider.targetKind() == null
                    || indexed.putIfAbsent(provider.targetKind(), provider) != null) {
                throw new IllegalArgumentException(
                        "one binding provider per target activity kind is required");
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public AgentWorldActivityBinding bind(AgentWorldActivityBindingRequest request) {
        if (request == null) throw new IllegalArgumentException("binding request is required");
        AgentWorldActivityBindingProvider provider = providers.get(request.targetActivityKind());
        if (provider == null) {
            throw new IllegalStateException(
                    "no World Director binding provider for " + request.targetActivityKind());
        }
        AgentWorldActivityBinding binding = provider.bind(request);
        if (binding == null) {
            throw new IllegalStateException(
                    "activity binding provider returned no ports for " + provider.targetKind());
        }
        return binding;
    }

    public boolean supports(AgentActivityKind kind) {
        return providers.containsKey(kind);
    }
}
