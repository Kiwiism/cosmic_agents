package client.command.commands.gm6;

/** Retained NuTNNuT command name; kept separate so Agent population can evolve independently. */
public final class BotPopCommand extends AgentPopCommand {
    public BotPopCommand() {
        setDescription("Legacy bot population management compatibility command.");
    }

    @Override
    protected String commandPrefix() {
        return "@botpop";
    }
}
