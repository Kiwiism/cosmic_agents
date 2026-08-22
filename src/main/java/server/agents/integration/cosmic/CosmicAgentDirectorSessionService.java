package server.agents.integration.cosmic;

import client.Character;
import net.server.Server;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.control.AgentWorldDirectorControlService;
import server.agents.runtime.activity.control.AgentWorldDirectiveJourneyRecorder;
import server.agents.runtime.activity.control.AgentDirectorSessionPort;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.journey.AgentFileJourneyJournalStore;

import java.sql.SQLException;
import java.util.Map;

/** Loads a selected offline backing character into a neutral, Director-owned live session. */
public final class CosmicAgentDirectorSessionService implements AgentDirectorSessionPort {
    @Override
    public AgentDirectorSessionPort.SpawnResult spawnIdle(
            int characterId, int world, int channel, long nowMs) {
        if (characterId <= 0 || world < 0 || channel <= 0 || nowMs < 0L) {
            return AgentDirectorSessionPort.SpawnResult.rejected(
                    "valid Agent identity, world, channel, and time are required");
        }
        AgentRuntimeEntry existing = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        if (existing != null && existing.bot() != null) {
            return AgentDirectorSessionPort.SpawnResult.live(
                    existing.bot().getId(), existing.bot().getName(), true,
                    "Agent already has a live runtime session");
        }
        Character alreadyOnline = AgentCharacterGatewayRuntime.characters()
                .findOnlineCharacterById(characterId);
        if (alreadyOnline != null) {
            return AgentDirectorSessionPort.SpawnResult.rejected(
                    "Agent character is already online outside a Director runtime session");
        }
        if (Server.getInstance().getWorld(world) == null
                || Server.getInstance().getChannel(world, channel) == null) {
            return AgentDirectorSessionPort.SpawnResult.rejected(
                    "requested world or channel is unavailable");
        }
        AgentResolvedCharacter resolved;
        try {
            resolved = AgentPersistenceGatewayRuntime.persistence().findCharacterById(characterId);
            if (resolved == null) return AgentDirectorSessionPort.SpawnResult.rejected(
                    "unknown backing character");
            if (!CosmicAgentBackingAccountSecurity.isAgentOnlyAccount(resolved.accountId())) {
                return AgentDirectorSessionPort.SpawnResult.rejected(
                        "character is not on an Agent-only backing account");
            }
        } catch (SQLException failure) {
            return AgentDirectorSessionPort.SpawnResult.rejected(
                    "could not validate backing character: " + failure.getMessage());
        }
        Character agent = null;
        try {
            // Null destination preserves the stored map and ordinary player-spawn placement.
            agent = CosmicAgentOfflineLoader.loadOfflineAgent(
                    characterId, world, channel, null, null);
            AgentRuntimeEntry entry = AgentInteractionRuntime.registerDirectorIdleAgent(agent);
            AgentWorldDirectorControlService control = AgentWorldDirectorControlService.runtimeDefault();
            control.setMode(characterId, AgentWorldDirectorMode.MANUAL,
                    "spawned for manual Director control", nowMs);
            AgentWorldDirective park = new AgentWorldDirective(
                    1, "director-spawn-park:" + characterId + ':' + entry.sessionGeneration(),
                    characterId, AgentWorldDirectiveType.SUSPEND_ACTIVITY,
                    AgentWorldDirectiveSource.OPERATOR, null, null, null, "", Map.of(),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                    1_000, nowMs, 0L,
                    "retain restored work and park safely for operator selection");
            var submitted = control.submit(park, nowMs);
            new AgentWorldDirectiveJourneyRecorder(
                    new AgentFileJourneyJournalStore()).submitted(submitted, nowMs);
            return AgentDirectorSessionPort.SpawnResult.live(characterId, agent.getName(), false,
                    "Agent loaded on its stored map; safe parking was queued");
        } catch (Exception | Error failure) {
            AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
            if (agent != null && agent.getClient() != null) agent.getClient().forceDisconnect();
            return AgentDirectorSessionPort.SpawnResult.rejected(
                    "could not create Director session: " + failure.getMessage());
        }
    }
}
