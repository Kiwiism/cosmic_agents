package server.agents.capabilities.combat;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentCombatGatewayRuntime;
import server.agents.integration.CombatGateway;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentAttackExecutionProviderRoutingTest {
    @Test
    void preparesObservedMobReactionBeforeDispatchingEveryAttackRoute() {
        Character agent = mock(Character.class);
        AbstractDealDamageHandler.AttackInfo attack =
                new AbstractDealDamageHandler.AttackInfo();
        CombatGateway combatGateway = mock(CombatGateway.class);
        AtomicBoolean prepared = new AtomicBoolean();

        try (MockedStatic<AgentMobHitReactionService> reactions =
                     mockStatic(AgentMobHitReactionService.class);
             MockedStatic<AgentCombatGatewayRuntime> runtime =
                     mockStatic(AgentCombatGatewayRuntime.class)) {
            reactions.when(() -> AgentMobHitReactionService.prepare(attack, agent))
                    .thenAnswer(invocation -> {
                        prepared.set(true);
                        return null;
                    });
            runtime.when(AgentCombatGatewayRuntime::combat).thenReturn(combatGateway);

            for (AgentAttackRoute route : AgentAttackRoute.values()) {
                prepared.set(false);
                AgentAttackExecutionProvider.applyAttackRoute(route, attack, agent);
                assertTrue(prepared.get(), () -> route + " dispatched before preparation");
            }

            reactions.verify(() -> AgentMobHitReactionService.prepare(attack, agent),
                    org.mockito.Mockito.times(AgentAttackRoute.values().length));
        }
    }
}
