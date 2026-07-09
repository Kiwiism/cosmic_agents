package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.trade.AgentManualOwnerTradeService.OwnerTradeCallbacks;
import server.agents.capabilities.trade.AgentManualPeerTradeService.PeerTradeCallbacks;
import server.agents.capabilities.trade.AgentManualTradeTickService.ManualTradeTickCallbacks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentManualTradeCallbackServiceTest {
    @Test
    void buildsManualTradeTickCallbacksFromLegacyOperations() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AtomicReference<Character> cleared = new AtomicReference<>();
        AtomicReference<AgentTradeWindow> ownerTrade = new AtomicReference<>(trade);
        AtomicBoolean peerTicked = new AtomicBoolean();
        AtomicBoolean ownerTicked = new AtomicBoolean();

        ManualTradeTickCallbacks callbacks = AgentManualTradeCallbackService.manualTradeTickCallbacks(
                () -> true,
                ignored -> trade,
                cleared::set,
                (currentAgent, currentTrade) -> currentAgent == agent && currentTrade == trade,
                ignored -> ownerTrade.get(),
                (currentAgent, currentOwner, currentTrade, ownerTradeActive) -> {
                    peerTicked.set(currentAgent == agent
                            && currentOwner == owner
                            && currentTrade == trade
                            && ownerTradeActive);
                    return true;
                },
                (currentAgent, currentOwner, currentTrade) ->
                        ownerTicked.set(currentAgent == agent && currentOwner == owner && currentTrade == trade));

        assertTrue(callbacks.hasActiveSequence());
        assertSame(trade, callbacks.agentTrade(agent));
        callbacks.clearState(agent);
        assertTrue(callbacks.beginOrTickTimeout(agent, trade));
        assertSame(trade, callbacks.ownerTrade(owner));
        assertTrue(callbacks.tickPeerTrade(agent, owner, trade, true));
        callbacks.tickOwnerTrade(agent, owner, trade);

        assertSame(agent, cleared.get());
        assertTrue(peerTicked.get());
        assertTrue(ownerTicked.get());
    }

    @Test
    void buildsPeerTradeCallbacksFromLegacyOperations() {
        Character peer = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AgentTradeWindow accepted = mock(AgentTradeWindow.class);
        AtomicReference<AgentTradeWindow> completed = new AtomicReference<>();
        AtomicReference<Character> refilled = new AtomicReference<>();
        AtomicReference<Character> cleared = new AtomicReference<>();

        PeerTradeCallbacks callbacks = AgentManualTradeCallbackService.peerTradeCallbacks(
                currentPeer -> currentPeer == peer,
                (peerId, ownerId) -> peerId == 1 && ownerId == 2,
                (inviter, pendingTrade) -> inviter == peer && pendingTrade == trade ? accepted : null,
                completed::set,
                refilled::set,
                cleared::set);

        assertTrue(callbacks.isPeerAgent(peer));
        assertTrue(callbacks.isAuthorizedPeer(1, 2));
        assertSame(accepted, callbacks.acceptInvite(peer, trade));
        callbacks.completeTrade(trade);
        callbacks.refillEquipment(owner);
        callbacks.clearGreeting(peer);

        assertSame(trade, completed.get());
        assertSame(owner, refilled.get());
        assertSame(peer, cleared.get());
    }

    @Test
    void buildsOwnerTradeCallbacksFromLegacyOperations() {
        Character inviter = mock(Character.class);
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentTradeWindow trade = mock(AgentTradeWindow.class);
        AgentTradeWindow accepted = mock(AgentTradeWindow.class);
        AtomicReference<Supplier<String>> greeting = new AtomicReference<>();
        AtomicReference<AgentTradeWindow> completed = new AtomicReference<>();
        AtomicReference<Character> refilled = new AtomicReference<>();

        OwnerTradeCallbacks callbacks = AgentManualTradeCallbackService.ownerTradeCallbacks(
                (currentInviter, pendingTrade) -> currentInviter == inviter && pendingTrade == trade ? accepted : null,
                (currentAgent, currentTrade, currentGreeting) -> {
                    assertSame(agent, currentAgent);
                    assertSame(trade, currentTrade);
                    greeting.set(currentGreeting);
                },
                () -> "hi",
                completed::set,
                refilled::set);

        assertSame(accepted, callbacks.acceptInvite(inviter, trade));
        callbacks.sendGreeting(agent, trade, callbacks.manualTradeGreeting());
        assertEquals("hi", greeting.get().get());
        callbacks.completeTrade(trade);
        callbacks.refillEquipment(owner);

        assertSame(trade, completed.get());
        assertSame(owner, refilled.get());
    }
}
