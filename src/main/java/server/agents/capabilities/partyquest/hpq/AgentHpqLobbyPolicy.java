package server.agents.capabilities.partyquest.hpq;

import client.Character;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/** HPQ-only capacity policy shared by its event script and background director. */
public final class AgentHpqLobbyPolicy {
    private AgentHpqLobbyPolicy() {
    }

    public static int maxLobbies() {
        return Math.max(1, Math.min(8, config.AgentTuning.intValue(
                "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyPolicy.MAX_LOBBIES")));
    }

    static boolean backgroundSlotAvailable(int world, int channel) {
        int total = 0;
        int onChannel = 0;
        for (AgentHpqSession session : AgentHpqSessionRegistry.sessions()) {
            if (session.mode() != AgentHpqSession.Mode.BACKGROUND_POPULATION) continue;
            total++;
            Character anchor = anchor(session);
            if (anchor != null
                    && AgentClientGatewayRuntime.clients().world(anchor) == world
                    && AgentClientGatewayRuntime.clients().channel(anchor) == channel) {
                onChannel++;
            }
        }
        return backgroundSlotAvailable(total, onChannel, maxLobbies(),
                config.AgentTuning.intValue(
                        "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyPolicy.MAX_BACKGROUND_TOTAL"),
                config.AgentTuning.intValue(
                        "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyPolicy.MAX_BACKGROUND_PER_CHANNEL"),
                config.AgentTuning.intValue(
                        "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyPolicy.HUMAN_RESERVED_LOBBIES"));
    }

    static boolean backgroundSlotAvailable(int total, int onChannel, int maxLobbies,
                                           int globalLimit, int configuredPerChannel,
                                           int humanReservedLobbies) {
        int safeGlobalLimit = Math.max(0, globalLimit);
        int safeConfiguredPerChannel = Math.max(0, configuredPerChannel);
        int safeReserved = Math.max(0, humanReservedLobbies);
        int perChannelLimit = Math.min(safeConfiguredPerChannel,
                Math.max(0, maxLobbies - safeReserved));
        return total < safeGlobalLimit && onChannel < perChannelLimit;
    }

    private static Character anchor(AgentHpqSession session) {
        for (AgentHpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            Character character = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
            if (character != null) return character;
        }
        return null;
    }
}
