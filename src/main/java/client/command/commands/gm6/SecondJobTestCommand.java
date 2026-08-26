package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.progression.AgentSecondJobCohortService;

/** GM-only Explorer second-job advancement harness. */
public final class SecondJobTestCommand extends Command {
    {
        setDescription("Run and inspect Explorer second-job advancements.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentSecondJobCohortService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
