package server.agents.capabilities.quest;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmherstTestRuntimeResetServiceTest {
    private record Command() implements AgentCapabilityCommand {
        @Override
        public String type() {
            return "test";
        }
    }

    @Test
    void clearsCapabilityModesTargetsCooldownsAndPendingFixtureWork() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(12, 34));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentExecutableCapability<Command> running = new AgentExecutableCapability<>() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public AgentCapabilityStep tick(server.agents.capabilities.runtime.AgentCapabilityContext context,
                                            Command command) {
                return AgentCapabilityStep.running("running");
            }
        };
        server.agents.capabilities.runtime.AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(running, new Command(), 1000L, 0));
        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, Set.of(100100));
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, mock(MapItem.class));
        AgentPendingActionStateRuntime.setPendingAction(entry, "trade");
        AgentPendingActionStateRuntime.setPendingDropCategory(entry, "etc");
        entry.combatCooldownState().setAttackCooldownMs(100);
        entry.combatCooldownState().setMoveWindowMs(100);
        entry.combatCooldownState().setMobHitCooldownMs(100);
        entry.inventoryCooldownState().setLootInhibitMs(100);
        entry.portalCooldownState().setUseCooldownUntilMs(1000L);
        entry.pendingLootOfferState().set(mock(client.inventory.Item.class), 1, 1000L, true);

        AmherstTestRuntimeResetService.reset(entry, agent, 100L);

        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 999999));
        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
        assertFalse(AgentPendingActionStateRuntime.hasPendingAction(entry));
        assertNull(AgentPendingActionStateRuntime.pendingDropCategory(entry));
        assertFalse(entry.combatCooldownState().hasAttackCooldown());
        assertFalse(entry.combatCooldownState().hasMoveWindow());
        assertFalse(entry.combatCooldownState().hasMobHitCooldown());
        assertTrue(entry.inventoryCooldownState().lootInhibitMs() == 0);
        assertFalse(entry.portalCooldownState().onCooldown(0L));
        assertNull(entry.pendingLootOfferState().item());
        assertFalse(entry.actionMailbox().isClosed());
    }
}
