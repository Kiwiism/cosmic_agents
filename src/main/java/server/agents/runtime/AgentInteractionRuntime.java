package server.agents.runtime;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentLifecycleCommandCoordinator;
import server.agents.capabilities.dialogue.AgentChatRouteCoordinator;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.cosmic.CosmicAgentSpawnCoordinator;
import server.agents.integration.cosmic.CosmicAgentReloginCoordinator;

/**
 * Agent-owned public runtime entry points for server integrations that still
 * need legacy AgentRuntimeEntry-backed behavior.
 */
public final class AgentInteractionRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentInteractionRuntime.class);

    private AgentInteractionRuntime() {
    }

    public static void registerAgent(int leaderCharId, Character leader, Character agent) {
        AgentRegistrationCoordinator.registerManualAgent(
                leaderCharId,
                leader,
                agent,
                AgentInteractionRuntime::tick);
    }

    public static AgentRuntimeEntry registerSpawnedAgent(int leaderCharId, Character leader, Character agent) {
        return AgentRegistrationCoordinator.registerSpawnedAgent(
                leaderCharId,
                leader,
                agent,
                AgentInteractionRuntime::tick);
    }

    /** Registers an autonomous population Agent using itself as its runtime anchor. */
    public static AgentRuntimeEntry registerSelfDirectedAgent(Character agent) {
        AgentRuntimeEntry entry = AgentRegistrationCoordinator.registerSpawnedAgent(
                agent.getId(), agent, agent, AgentInteractionRuntime::tick);
        AgentMovementCommandRuntime.grind(entry);
        return entry;
    }

    public static AgentLifecycleService.AgentSpawnResult spawnAgentForLeader(Character leader, String agentName) {
        return CosmicAgentSpawnCoordinator.spawnAgentForLeader(
                leader,
                agentName,
                AgentInteractionRuntime::tick,
                AgentMovementCommandRuntime::followOwner,
                log);
    }

    /**
     * Partner-specific registration marks the entry before its first scheduled
     * tick, so generic Agent inventory/progression automation never gets a
     * one-tick window during Double Partner activation.
     */
    public static AgentLifecycleService.AgentSpawnResult spawnPartnerAgentForLeader(
            Character leader,
            String agentName) {
        AgentLifecycleService.RegisterSpawnedAgent registerPartner =
                (leaderCharId, spawnLeader, agent) ->
                    AgentRegistrationCoordinator.registerPartnerAgent(
                            leaderCharId,
                            spawnLeader,
                            agent,
                            true,
                            AgentInteractionRuntime::tick);
        return CosmicAgentSpawnCoordinator.spawnAgentForLeader(
                leader,
                agentName,
                registerPartner,
                AgentMovementCommandRuntime::followOwner,
                log);
    }

    public static void handleLeaderChat(Character leader, String message, AgentReplyChannel channel) {
        AgentChatRouteCoordinator.handleChat(
                leader,
                message,
                channel,
                AgentInteractionRuntime::recruitAgent,
                AgentInteractionRuntime::transferAgent,
                AgentInteractionRuntime::dismissAgent);
    }

    public static void reloginAgent(int agentCharId, int leaderCharId, int world, int channel) {
        CosmicAgentReloginCoordinator.reloginAgent(
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
                AgentMovementCommandRuntime::grind,
                AgentMovementCommandRuntime::followOwner);
    }

    private static String recruitAgent(int leaderCharId, Character leader, String agentName) {
        return AgentLifecycleCommandCoordinator.recruitAgent(
                leaderCharId,
                leader,
                agentName,
                (leaderId, recruitLeader, agent) -> AgentRegistrationCoordinator.registerManualAgent(
                        leaderId,
                        recruitLeader,
                        agent,
                        AgentInteractionRuntime::tick));
    }

    private static String transferAgent(int leaderCharId, Character leader, String agentName, String targetName) {
        return AgentLifecycleCommandCoordinator.transferAgent(
                leaderCharId,
                leader,
                agentName,
                targetName,
                AgentMovementCommandRuntime::stop,
                (leaderId, transferLeader, agent) -> AgentRegistrationCoordinator.registerManualAgent(
                        leaderId,
                        transferLeader,
                        agent,
                        AgentInteractionRuntime::tick));
    }

    private static boolean dismissAgent(int leaderCharId, String agentName) {
        return AgentLifecycleCommandCoordinator.dismissAgent(
                leaderCharId,
                agentName,
                AgentMovementCommandRuntime::stop);
    }
}
