package server.agents.commands;

import client.Character;
import client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.AgentBackingAccountSecurityRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.auth.AgentOwnershipService;
import server.agents.auth.AgentProvisioningPolicy;
import java.sql.SQLException;

/** Translates the retained spawn command into Agent account, lifecycle, and party operations. */
public final class AgentSpawnCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentSpawnCommandExecutor.class);
    private static final AgentProvisioningPolicy PROVISIONING_POLICY = new AgentProvisioningPolicy();

    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        AgentOwnershipService ownershipService = AgentOwnershipService.getInstance();
        if (params.length < 1) {
            player.yellowMessage("Syntax: @spawnbot <name> [confirm]");
            return;
        }

        String[] rawArgs = player.getLastCommandMessage().trim().split("[ ]", 2);
        String botName = rawArgs[0];
        boolean createRequested = params.length >= 2 && params[1].equals("confirm");

        AgentResolvedCharacter bot = ownershipService.resolveCharacterByName(botName);
        if (bot == null) {
            if (!createRequested) {
                player.yellowMessage("Bot '" + botName + "' does not exist. Run: @spawnbot " + botName + " confirm  to create it.");
                return;
            }

            String denial = validateProvisioning(player);
            if (denial != null) {
                player.yellowMessage(denial);
                return;
            }

            AgentAccountResolution account = resolveAgentAccount(botName);
            if (!account.isSuccess()) {
                player.yellowMessage(account.failureMessage());
                return;
            }
            if (!lockAgentBackingAccount(account.accountId())) {
                player.yellowMessage("Failed to secure the Agent-only backing account for '" + botName + "'.");
                return;
            }

            Client creationClient = AgentClientGatewayRuntime.clients()
                    .createHeadlessClient(c.getWorld(), c.getChannel());
            creationClient.setAccID(account.accountId());
            creationClient.setAccountName(botName);

            int createdCharId = AgentClientGatewayRuntime.clients().createBackingCharacter(creationClient, botName);
            if (createdCharId == -1) {
                player.yellowMessage("Failed to create bot character '" + botName + "'. Name may be invalid or already taken.");
                return;
            }

            ownershipService.registerOwner(createdCharId, player.getId());
            bot = ownershipService.resolveCharacterByName(botName);
            if (account.created()) {
                player.yellowMessage("Bot '" + botName + "' created with an Agent-only backing account.");
            } else {
                player.yellowMessage("Bot '" + botName + "' created on the existing empty account '" + botName + "'.");
            }
        }

        AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime.spawnAgentForLeader(player, botName);
        if (!result.success()) {
            player.yellowMessage(result.errorMessage());
            return;
        }
        AgentPartyLifecycleService.joinAgentToLeaderParty(player, result.agent());
        if (result.autoRegistered()) {
            player.yellowMessage("Bot '" + result.agent().getName() + "' auto-registered to " + player.getName() + " because it is on the same account.");
        }
        player.yellowMessage("Bot '" + result.agent().getName() + "' spawned. Say 'follow me' or 'stop' to control it.");
    }

    private String validateProvisioning(Character player) {
        try {
            int registeredAgents = AgentPersistenceGatewayRuntime.persistence()
                    .countRegisteredAgents(player.getId());
            return PROVISIONING_POLICY.validateAndRecordAttempt(
                    player.getId(), player.gmLevel(), registeredAgents);
        } catch (SQLException e) {
            log.warn("Failed to verify Agent provisioning quota for character {}", player.getId(), e);
            return "Failed to verify Agent backing-account creation policy.";
        }
    }

    private AgentAccountResolution resolveAgentAccount(String name) {
        try {
            return AgentPersistenceGatewayRuntime.persistence().resolveOrCreateAgentAccount(name);
        } catch (SQLException e) {
            log.warn("Failed to create or reuse bot account '{}'", name, e);
        }
        return AgentAccountResolution.failure("Failed to create or reuse the bot account for '" + name + "'.");
    }

    private boolean lockAgentBackingAccount(int accountId) {
        try {
            return AgentBackingAccountSecurityRuntime.lockInteractiveLogin(accountId);
        } catch (SQLException e) {
            log.warn("Failed to lock interactive login for Agent backing account {}", accountId, e);
            return false;
        }
    }
}
