package server.agents.integration.cosmic;

import client.Character;
import net.server.Server;
import server.agents.auth.AgentOwnershipService;
import server.agents.population.AgentPopulationRecord;
import server.agents.population.AgentPopulationSessionService;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.sql.SQLException;

/** Cosmic lifecycle adapter for self-directed population sessions. */
public final class CosmicAgentPopulationBackend implements AgentPopulationSessionService.Backend {
    public static final int DEFAULT_WORLD = 0;
    public static final int DEFAULT_CHANNEL = 1;

    interface Hooks {
        AgentResolvedCharacter resolve(int characterId);
        boolean isAgentOnlyAccount(int accountId) throws SQLException;
        Character online(int characterId);
        Character load(int characterId, int world, int channel) throws SQLException;
        AgentRuntimeEntry register(int characterId, Character agent);
        void startSelfDirected(AgentRuntimeEntry entry);
        void removeRuntime(int characterId);
        void disconnect(Character agent);
        boolean channelAvailable(int world, int channel);
    }

    private final int world;
    private final int channel;
    private final Hooks hooks;

    public CosmicAgentPopulationBackend() {
        this(DEFAULT_WORLD, DEFAULT_CHANNEL, defaultHooks());
    }

    CosmicAgentPopulationBackend(int world, int channel, Hooks hooks) {
        this.world = world;
        this.channel = channel;
        this.hooks = hooks;
    }

    @Override
    public boolean isEligibleAgent(int characterId) {
        AgentResolvedCharacter resolved = hooks.resolve(characterId);
        if (resolved == null) {
            return false;
        }
        Character online = hooks.online(characterId);
        if (online != null) {
            return CosmicCharacterGateway.INSTANCE.isAgentCharacter(online);
        }
        try {
            return hooks.isAgentOnlyAccount(resolved.accountId());
        } catch (SQLException failure) {
            return false;
        }
    }

    @Override
    public boolean isLive(int characterId) {
        return AgentRuntimeRegistry.hasActiveAgentCharacterId(characterId);
    }

    @Override
    public boolean spawnSelfDirected(AgentPopulationRecord record) throws Exception {
        if (!hooks.channelAvailable(world, channel) || hooks.online(record.characterId()) != null) {
            return false;
        }
        Character agent = null;
        try {
            agent = hooks.load(record.characterId(), world, channel);
            AgentRuntimeEntry entry = hooks.register(record.characterId(), agent);
            hooks.startSelfDirected(entry);
            return true;
        } catch (Exception | Error failure) {
            hooks.removeRuntime(record.characterId());
            if (agent != null) {
                hooks.disconnect(agent);
            }
            throw failure;
        }
    }

    @Override
    public boolean stop(int characterId) {
        Character agent = hooks.online(characterId);
        if (agent == null || !CosmicCharacterGateway.INSTANCE.isAgentCharacter(agent)) {
            return false;
        }
        hooks.removeRuntime(characterId);
        hooks.disconnect(agent);
        return true;
    }

    private static Hooks defaultHooks() {
        return new Hooks() {
            @Override public AgentResolvedCharacter resolve(int characterId) {
                return AgentOwnershipService.getInstance().resolveCharacterById(characterId);
            }
            @Override public boolean isAgentOnlyAccount(int accountId) throws SQLException {
                return CosmicAgentBackingAccountSecurity.isAgentOnlyAccount(accountId);
            }
            @Override public Character online(int characterId) {
                return CosmicCharacterGateway.INSTANCE.findOnlineCharacterById(characterId);
            }
            @Override public Character load(int characterId, int world, int channel) throws SQLException {
                return CosmicAgentOfflineLoader.loadOfflineAgent(characterId, world, channel, null, null);
            }
            @Override public AgentRuntimeEntry register(int characterId, Character agent) {
                return AgentInteractionRuntime.registerSelfDirectedAgent(agent);
            }
            @Override public void startSelfDirected(AgentRuntimeEntry entry) {
                // registerSelfDirectedAgent enters grind mode before publishing the session.
            }
            @Override public void removeRuntime(int characterId) {
                AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
            }
            @Override public void disconnect(Character agent) {
                agent.getClient().forceDisconnect();
            }
            @Override public boolean channelAvailable(int world, int channel) {
                return Server.getInstance().getWorld(world) != null
                        && Server.getInstance().getChannel(world, channel) != null;
            }
        };
    }
}
