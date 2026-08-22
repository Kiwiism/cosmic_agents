package server.agents.runtime.activity.control;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.sql.SQLException;
import java.util.List;

/** Read-only Agent-only database roster with live runtime projection. */
public final class AgentWorldDirectorAgentDirectory {
    private final AgentPersistenceGateway persistence;

    public AgentWorldDirectorAgentDirectory(AgentPersistenceGateway persistence) {
        if (persistence == null) throw new IllegalArgumentException("persistence gateway is required");
        this.persistence = persistence;
    }

    public List<AgentDirectorAgentDirectoryEntry> list() {
        try {
            return persistence.listAgentCharacters().stream().map(summary -> {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(
                        summary.characterId());
                Character live = AgentRuntimeIdentityRuntime.bot(entry);
                if (live == null) {
                    live = AgentCharacterGatewayRuntime.characters()
                            .findOnlineCharacterById(summary.characterId());
                }
                return new AgentDirectorAgentDirectoryEntry(
                        summary.characterId(), summary.name(),
                        live == null ? summary.level() : live.getLevel(),
                        live == null ? summary.jobId() : live.getJob().getId(),
                        live == null ? summary.mapId() : live.getMapId(),
                        live != null, entry != null);
            }).toList();
        } catch (SQLException failure) {
            throw new IllegalStateException("could not load the Agent Director roster", failure);
        }
    }
}
