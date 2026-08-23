package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.progression.AgentMushroomKingdomCohortService;

/** GM-only Mushroom Kingdom twelve-branch observation harness. */
public final class MushroomTestCommand extends Command {
    {
        setDescription("Run and inspect the 12-branch Mushroom Kingdom Agent cohort.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentMushroomKingdomCohortService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
