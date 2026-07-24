package server.agents.capabilities.runtime;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative defaults for legacy capabilities that have not yet declared
 * an explicit resource set. New capabilities should override
 * {@link AgentExecutableCapability#requiredResources(AgentCapabilityCommand)}.
 */
public final class AgentCapabilityResourcePolicy {
    private AgentCapabilityResourcePolicy() {
    }

    public static Set<AgentCapabilityResource> defaultsFor(String capabilityId) {
        String id = capabilityId == null ? "" : capabilityId.toLowerCase(Locale.ROOT);
        EnumSet<AgentCapabilityResource> resources =
                EnumSet.noneOf(AgentCapabilityResource.class);
        if (containsAny(id, "navigation", "travel", "portal", "move", "recovery")) {
            resources.add(AgentCapabilityResource.MOVEMENT);
        }
        if (containsAny(id, "combat", "attack", "reactor")) {
            resources.add(AgentCapabilityResource.COMBAT);
            resources.add(AgentCapabilityResource.MOVEMENT);
        }
        if (containsAny(id, "inventory", "item", "loot", "shop", "stall", "quest")) {
            resources.add(AgentCapabilityResource.INVENTORY);
        }
        if (containsAny(id, "npc", "quest", "shop")) {
            resources.add(AgentCapabilityResource.NPC_INTERACTION);
            resources.add(AgentCapabilityResource.MOVEMENT);
        }
        if (containsAny(id, "trade", "party", "buddy", "guild", "stall")) {
            resources.add(AgentCapabilityResource.SOCIAL_TRANSACTION);
        }
        return resources.isEmpty() ? Set.of() : Set.copyOf(resources);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
