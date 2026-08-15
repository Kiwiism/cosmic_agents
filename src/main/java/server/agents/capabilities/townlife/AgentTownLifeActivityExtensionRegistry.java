package server.agents.capabilities.townlife;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validated registry; profile handler ids never become core-runtime switch cases. */
public final class AgentTownLifeActivityExtensionRegistry {
    private static final AgentTownLifeActivityExtensionRegistry DEFAULT =
            new AgentTownLifeActivityExtensionRegistry(java.util.List.of());

    private final Map<String, AgentTownLifeActivityExtension> byId;

    public AgentTownLifeActivityExtensionRegistry(
            Collection<? extends AgentTownLifeActivityExtension> extensions) {
        Map<String, AgentTownLifeActivityExtension> index = new LinkedHashMap<>();
        for (AgentTownLifeActivityExtension extension : extensions == null
                ? java.util.List.<AgentTownLifeActivityExtension>of() : extensions) {
            if (extension == null || extension.id() == null || extension.id().isBlank()) {
                throw new IllegalArgumentException("TownLife activity extension id is required");
            }
            if (index.putIfAbsent(extension.id(), extension) != null) {
                throw new IllegalArgumentException("duplicate TownLife activity extension " + extension.id());
            }
        }
        byId = Map.copyOf(index);
    }

    public static AgentTownLifeActivityExtensionRegistry defaultRegistry() {
        return DEFAULT;
    }

    public Optional<AgentTownLifeActivityExtension> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Set<String> ids() {
        return byId.keySet();
    }
}
