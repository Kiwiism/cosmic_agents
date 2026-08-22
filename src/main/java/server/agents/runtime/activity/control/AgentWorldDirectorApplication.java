package server.agents.runtime.activity.control;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;

import java.util.List;

/** Single use-case surface shared by every Director transport and presentation. */
public final class AgentWorldDirectorApplication {
    private final AgentWorldDirectorAgentDirectory directory;
    private final AgentDirectorSessionPort sessions;
    private final AgentWorldDirectorExecutive executive;

    public AgentWorldDirectorApplication(
            AgentWorldDirectorAgentDirectory directory,
            AgentDirectorSessionPort sessions,
            AgentWorldDirectorExecutive executive) {
        if (directory == null || sessions == null || executive == null) {
            throw new IllegalArgumentException("complete Director application services are required");
        }
        this.directory = directory;
        this.sessions = sessions;
        this.executive = executive;
    }

    public List<AgentDirectorAgentDirectoryEntry> agents() {
        return directory.list();
    }

    public AgentDirectorSessionPort.SpawnResult spawnIdle(
            int characterId, int world, int channel, long nowMs) {
        return sessions.spawnIdle(characterId, world, channel, nowMs);
    }

    public AgentDirectorExecutiveView view(
            AgentRuntimeEntry entry, Character agent, int journeyLimit, long nowMs) {
        requireMatchingIdentity(entry, agent);
        return executive.view(entry, agent, journeyLimit, nowMs);
    }

    /** Transport-neutral lookup used by panel, WASM, in-game, and future LLM clients. */
    public AgentDirectorExecutiveView view(
            int characterId, int journeyLimit, long nowMs) {
        LiveAgent live = requireLive(characterId);
        return executive.view(live.entry(), live.agent(), journeyLimit, nowMs);
    }

    public AgentWorldDirectorSession setMode(
            AgentRuntimeEntry entry,
            Character agent,
            AgentWorldDirectorMode mode,
            String reason,
            long nowMs) {
        requireMatchingIdentity(entry, agent);
        return executive.setMode(entry, agent, mode, reason, nowMs);
    }

    public AgentWorldDirectorSession setMode(
            int characterId, AgentWorldDirectorMode mode, String reason, long nowMs) {
        LiveAgent live = requireLive(characterId);
        return executive.setMode(live.entry(), live.agent(), mode, reason, nowMs);
    }

    public AgentWorldDirectiveEnvelope execute(
            AgentRuntimeEntry entry,
            Character agent,
            String actionId,
            String expectedContextRevision,
            String idempotencyKey,
            String reason,
            boolean confirmDestructive,
            long nowMs) {
        requireMatchingIdentity(entry, agent);
        return executive.submit(entry, agent, actionId, expectedContextRevision,
                idempotencyKey, reason, confirmDestructive, nowMs);
    }

    public AgentWorldDirectiveEnvelope execute(
            int characterId,
            String actionId,
            String expectedContextRevision,
            String idempotencyKey,
            String reason,
            boolean confirmDestructive,
            long nowMs) {
        LiveAgent live = requireLive(characterId);
        return executive.submit(live.entry(), live.agent(), actionId, expectedContextRevision,
                idempotencyKey, reason, confirmDestructive, nowMs);
    }

    public AgentWorldDirectiveEnvelope cancel(
            int agentId, String directiveId, String reason, long nowMs) {
        return executive.cancel(agentId, directiveId, reason, nowMs);
    }

    public AgentActivityOutcomeEnvelope acknowledgeOutcome(
            int characterId, String outcomeId, String reason, long nowMs) {
        requireLive(characterId);
        return executive.acknowledgeOutcome(characterId, outcomeId, reason, nowMs);
    }

    private static LiveAgent requireLive(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null || agent.getId() != characterId) {
            throw new IllegalStateException(
                    "Agent is offline; create an idle Director session before executing actions");
        }
        return new LiveAgent(entry, agent);
    }

    private static void requireMatchingIdentity(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null
                || AgentRuntimeIdentityRuntime.bot(entry) != agent) {
            throw new IllegalArgumentException("Director runtime entry does not own this Agent");
        }
    }

    private record LiveAgent(AgentRuntimeEntry entry, Character agent) { }
}
