package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.diagnostics.skillprobe.AgentSkillProbeService;

/** GM-only per-job skill readiness probe for Agents; see AgentSkillProbeService. */
public final class SkillProbeCommand extends Command {
    {
        setDescription("Shape an Agent to a job/level and try every skill in its tree against a dummy mob.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentSkillProbeService.execute(operator, params)
                .forEach(line -> operator.dropMessage(6, line));
    }
}
