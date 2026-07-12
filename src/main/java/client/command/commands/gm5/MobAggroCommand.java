package client.command.commands.gm5;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.integration.AgentMobReactionGatewayRuntime;
import server.life.Monster;

public class MobAggroCommand extends Command {
    {
        setDescription("Show observer, controller, threshold, and logical target for a mob OID.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (params.length != 1) {
            player.dropMessage(5, "Syntax: !mobaggro <mob oid>");
            return;
        }

        final int oid;
        try {
            oid = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            player.dropMessage(5, "Mob OID must be an integer.");
            return;
        }

        Monster monster = player.getMap().getMonsterByOid(oid);
        if (monster == null) {
            player.dropMessage(5, "No monster with OID " + oid + " exists in this map.");
            return;
        }
        player.dropMessage(6, AgentMobReactionGatewayRuntime.mobReactions().describe(monster));
    }
}
