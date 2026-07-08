package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotMovementCommandRuntime;

/**
 * Agent-owned public runtime entry points for server integrations that still
 * need legacy AgentRuntimeEntry-backed behavior.
 */
public final class AgentInteractionRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentInteractionRuntime.class);

    private AgentInteractionRuntime() {
    }

    public static void registerAgent(int leaderCharId, Character leader, Character agent) {
        AgentRegistrationRuntime.registerManualAgent(
                leaderCharId,
                leader,
                agent,
                AgentInteractionRuntime::tick);
    }

    public static AgentRuntimeEntry registerSpawnedAgent(int leaderCharId, Character leader, Character agent) {
        return AgentRegistrationRuntime.registerSpawnedAgent(
                leaderCharId,
                leader,
                agent,
                AgentInteractionRuntime::tick);
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(Character leader, String agentName) {
        return AgentSpawnRuntime.spawnAgentForLeader(
                leader,
                agentName,
                AgentInteractionRuntime::tick,
                AgentBotMovementCommandRuntime::followOwner,
                log);
    }

    public static void handleLeaderChat(Character leader, String message, AgentReplyChannel channel) {
        AgentChatRouteRuntime.handleChat(
                leader,
                message,
                channel,
                AgentInteractionRuntime::recruitAgent,
                AgentInteractionRuntime::transferAgent,
                AgentInteractionRuntime::dismissAgent);
    }

    public static void reloginAgent(int agentCharId, int leaderCharId, int world, int channel) {
        AgentReloginRuntime.reloginAgent(
                agentCharId,
                leaderCharId,
                world,
                channel,
                AgentInteractionRuntime::tick,
                log);
    }

    private static void tick(AgentRuntimeEntry entry, int leaderCharId, int agentCharId) {
        AgentTickRuntime.tick(
                entry,
                leaderCharId,
                agentCharId,
                AgentBotMovementCommandRuntime::grind,
                AgentBotMovementCommandRuntime::followOwner);
    }

    private static String recruitAgent(int leaderCharId, Character leader, String agentName) {
        return AgentLifecycleChatCommandRuntime.recruitAgent(
                leaderCharId,
                leader,
                agentName,
                (leaderId, recruitLeader, agent) -> AgentRegistrationRuntime.registerManualAgent(
                        leaderId,
                        recruitLeader,
                        agent,
                        AgentInteractionRuntime::tick));
    }

    private static String transferAgent(int leaderCharId, Character leader, String agentName, String targetName) {
        return AgentLifecycleChatCommandRuntime.transferAgent(
                leaderCharId,
                leader,
                agentName,
                targetName,
                AgentBotMovementCommandRuntime::stop,
                (leaderId, transferLeader, agent) -> AgentRegistrationRuntime.registerManualAgent(
                        leaderId,
                        transferLeader,
                        agent,
                        AgentInteractionRuntime::tick));
    }

    private static boolean dismissAgent(int leaderCharId, String agentName) {
        return AgentLifecycleChatCommandRuntime.dismissAgent(
                leaderCharId,
                agentName,
                AgentBotMovementCommandRuntime::stop);
    }
}
