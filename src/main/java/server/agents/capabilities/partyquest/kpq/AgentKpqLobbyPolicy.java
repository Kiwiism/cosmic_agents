package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/** KPQ-only capacity policy; event scripts and background admission share one source of truth. */
public final class AgentKpqLobbyPolicy {
    private AgentKpqLobbyPolicy() {
    }

    public static int maxLobbies() {
        return Math.max(1, Math.min(8, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqLobbyPolicy.MAX_LOBBIES")));
    }

    static boolean backgroundSlotAvailable(int world, int channel) {
        int total = 0;
        int onChannel = 0;
        for (AgentKpqSession session : AgentKpqSessionRegistry.sessions()) {
            if (session.mode() != AgentKpqSession.Mode.BACKGROUND_POPULATION) continue;
            total++;
            Character anchor = anchor(session);
            if (anchor != null
                    && AgentClientGatewayRuntime.clients().world(anchor) == world
                    && AgentClientGatewayRuntime.clients().channel(anchor) == channel) {
                onChannel++;
            }
        }
        int globalLimit = Math.max(0, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqLobbyPolicy.MAX_BACKGROUND_TOTAL"));
        int configuredPerChannel = Math.max(0, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqLobbyPolicy.MAX_BACKGROUND_PER_CHANNEL"));
        int reserved = Math.max(0, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.kpq.AgentKpqLobbyPolicy.HUMAN_RESERVED_LOBBIES"));
        int perChannelLimit = Math.min(configuredPerChannel, Math.max(0, maxLobbies() - reserved));
        return total < globalLimit && onChannel < perChannelLimit;
    }

    private static Character anchor(AgentKpqSession session) {
        for (AgentKpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            Character character = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
            if (character != null) return character;
        }
        return null;
    }
}
