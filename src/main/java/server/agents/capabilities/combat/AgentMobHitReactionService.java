package server.agents.capabilities.combat;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.integration.AgentMobReactionGatewayRuntime;

public final class AgentMobHitReactionService {
    private AgentMobHitReactionService() {
    }

    public static void prepare(AbstractDealDamageHandler.AttackInfo attack, Character agent) {
        AgentMobReactionGatewayRuntime.mobReactions().prepareObservedAttack(attack, agent);
    }
}
