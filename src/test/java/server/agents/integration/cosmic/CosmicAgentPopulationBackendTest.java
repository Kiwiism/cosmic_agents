package server.agents.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.population.AgentPopulationRecord;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentRuntimeEntry;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CosmicAgentPopulationBackendTest {
    @Test
    void offlineCharacterRequiresAgentOnlyAccountLock() {
        StubHooks hooks = new StubHooks();
        hooks.resolved = new AgentResolvedCharacter(7, "agent", 70, null);
        CosmicAgentPopulationBackend backend = new CosmicAgentPopulationBackend(0, 1, hooks);

        assertFalse(backend.isEligibleAgent(7));
        hooks.agentOnly = true;
        assertTrue(backend.isEligibleAgent(7));
    }

    @Test
    void unavailableChannelDoesNotLoadCharacter() throws Exception {
        StubHooks hooks = new StubHooks();
        hooks.channelAvailable = false;
        CosmicAgentPopulationBackend backend = new CosmicAgentPopulationBackend(0, 1, hooks);

        assertFalse(backend.spawnSelfDirected(new AgentPopulationRecord(7, "agent", null)));
        assertFalse(hooks.loaded.get());
    }

    @Test
    void failedRegistrationCleansLoadedCharacter() {
        StubHooks hooks = new StubHooks();
        hooks.throwOnRegister = true;
        CosmicAgentPopulationBackend backend = new CosmicAgentPopulationBackend(0, 1, hooks);

        assertThrows(IllegalStateException.class,
                () -> backend.spawnSelfDirected(new AgentPopulationRecord(7, "agent", null)));
        assertTrue(hooks.runtimeRemoved.get());
        assertTrue(hooks.disconnected.get());
    }

    private static final class StubHooks implements CosmicAgentPopulationBackend.Hooks {
        AgentResolvedCharacter resolved;
        boolean agentOnly;
        boolean channelAvailable = true;
        boolean throwOnRegister;
        final Character loadedCharacter = newCharacter();
        final AtomicBoolean loaded = new AtomicBoolean();
        final AtomicBoolean runtimeRemoved = new AtomicBoolean();
        final AtomicBoolean disconnected = new AtomicBoolean();

        @Override public AgentResolvedCharacter resolve(int characterId) { return resolved; }
        @Override public boolean isAgentOnlyAccount(int accountId) throws SQLException { return agentOnly; }
        @Override public Character online(int characterId) { return null; }
        @Override public Character load(int characterId, int world, int channel) {
            loaded.set(true); return loadedCharacter;
        }
        @Override public AgentRuntimeEntry register(int characterId, Character agent) {
            if (throwOnRegister) throw new IllegalStateException("register failed");
            return new AgentRuntimeEntry(agent, agent, null);
        }
        @Override public void startSelfDirected(AgentRuntimeEntry entry) { }
        @Override public void removeRuntime(int characterId) { runtimeRemoved.set(true); }
        @Override public void disconnect(Character agent) { disconnected.set(true); }
        @Override public boolean channelAvailable(int world, int channel) { return channelAvailable; }

        private static Character newCharacter() {
            try {
                var constructor = Character.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
