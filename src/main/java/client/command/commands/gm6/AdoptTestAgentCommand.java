package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.auth.AgentControlService;
import server.agents.integration.AgentIdentityGatewayRuntime;
import server.agents.integration.AgentIdentityOrigin;
import server.agents.integration.AgentIdentityRecord;
import server.agents.registry.AgentResolvedCharacter;

import java.sql.SQLException;
import java.util.Optional;

/** Explicitly admits an offline, same-account legacy character as a test Agent. */
public final class AdoptTestAgentCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(AdoptTestAgentCommand.class);

    {
        setDescription("Adopt an offline character from your account as a legacy test Agent.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character actor = client.getPlayer();
        if (!AgentAuthorityService.mayOperate(actor)) {
            actor.yellowMessage("You are not configured as an Agent operator.");
            return;
        }
        if (params.length < 1) {
            actor.yellowMessage("Syntax: !adopttestagent <name> [confirm]");
            return;
        }

        String targetName = params[0];
        AgentResolvedCharacter target = AgentControlService.getInstance()
                .resolveCharacterByName(targetName);
        if (target == null) {
            actor.yellowMessage("Character '" + targetName + "' could not be found.");
            return;
        }
        if (target.id() == actor.getId()) {
            actor.yellowMessage("You cannot adopt your current character as a test Agent.");
            return;
        }
        if (target.accountId() != actor.getAccountID()) {
            actor.yellowMessage("Legacy test adoption is limited to characters on your own account.");
            return;
        }
        if (target.isOnline()) {
            actor.yellowMessage("Character '" + target.name() + "' must be offline before adoption.");
            return;
        }

        try {
            Optional<AgentIdentityRecord> identity = AgentIdentityGatewayRuntime.identities()
                    .find(target.id());
            if (identity.isPresent()) {
                actor.yellowMessage(identity.get().isActive()
                        ? "Character '" + target.name() + "' is already an active Agent."
                        : "Character '" + target.name() + "' has a retired Agent identity and cannot be adopted.");
                return;
            }
            if (params.length < 2 || !"confirm".equalsIgnoreCase(params[1])) {
                actor.yellowMessage("Adopt '" + target.name() + "' as an interactive-capable legacy test Agent.");
                actor.yellowMessage("Run: !adopttestagent " + target.name() + " confirm");
                return;
            }

            AgentIdentityGatewayRuntime.identities().register(
                    target.id(), AgentIdentityOrigin.LEGACY_TEST_FIXTURE, true);
            actor.yellowMessage("Character '" + target.name()
                    + "' is now registered as a legacy test Agent.");
            log.warn("Operator {} adopted same-account character {} ({}) as a legacy test Agent",
                    actor.getName(), target.name(), target.id());
        } catch (SQLException failure) {
            log.error("Failed to adopt legacy test Agent '{}'", target.name(), failure);
            actor.yellowMessage("The Agent identity could not be registered.");
        }
    }
}
