package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import constants.id.ItemId;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.economy.ambient.AmbientBehaviorPolicy;
import server.agents.economy.session.CommerceParticipant;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Detachable adapter from the economy's ambient policy to existing Cosmic physical capabilities. */
public final class CosmicMarketAmbientBehavior implements AutonomousFreeMarketBehavior.AmbientBehavior {
    private final AmbientBehaviorPolicy policy;

    public CosmicMarketAmbientBehavior(AmbientBehaviorPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public Result perform(Character agent, CommerceParticipant profile, Instant logicalAt,
                          boolean ownsOpenStall, boolean negotiating, int consecutiveActions) {
        Optional<Integer> chair = ownedChair(agent);
        AmbientBehaviorPolicy.Context context = new AmbientBehaviorPolicy.Context(profile.agentId(),
                logicalAt, agent.getMapId(), ownsOpenStall, negotiating, chair.isPresent(),
                agent.getChair() >= 0, consecutiveActions);
        Optional<AmbientBehaviorPolicy.AmbientAction> selected = policy.choose(context);
        if (selected.isEmpty()) return Result.none();
        AmbientBehaviorPolicy.AmbientAction action = selected.orElseThrow();
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        boolean success = switch (action.type()) {
            case IDLE -> true;
            case SIT -> chair.isPresent() && entry != null && AgentChairService.sit(entry, agent, chair.orElseThrow());
            case STAND -> entry != null && AgentChairService.stand(entry, agent);
            case FIDGET -> entry != null && AgentFidgetService.maybeStartSocialFidget(entry);
            case SHORT_WALK -> false;
        };
        return new Result(true, success, action.type().name(), action.reason(),
                chair.orElse(null), Map.of("ownsOpenStall", ownsOpenStall,
                        "runtimeEntryAvailable", entry != null));
    }

    private static Optional<Integer> ownedChair(Character agent) {
        return agent.getInventory(InventoryType.SETUP).list().stream()
                .map(item -> item.getItemId()).filter(ItemId::isChair)
                .min(Comparator.naturalOrder());
    }
}
