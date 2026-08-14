package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.economy.integration.cosmic.EconomySimulationRuntime;

/** Operator control only; economic decisions and market participation remain autonomous. */
public final class EconomyCommand extends Command {
    public EconomyCommand() { setDescription("Control the autonomous economy run: start|advance <days>|status|stop"); }

    @Override
    public void execute(Client client, String[] params) {
        try {
            if (params.length == 0 || "status".equalsIgnoreCase(params[0])) {
                show(client, EconomySimulationRuntime.status()); return;
            }
            if ("start".equalsIgnoreCase(params[0])) {
                show(client, EconomySimulationRuntime.start()); return;
            }
            if ("advance".equalsIgnoreCase(params[0]) && params.length == 2) {
                long days = Long.parseLong(params[1]);
                var result = EconomySimulationRuntime.advanceDays(days);
                client.getPlayer().yellowMessage("Economy reached " + result.advance().reachedAt()
                        + "; events=" + result.advance().processedEvents() + "; status=" + result.status()
                        + (result.advance().waitingExternalAction()
                        ? "; waiting=" + result.advance().waitReason() : ""));
                return;
            }
            if ("stop".equalsIgnoreCase(params[0])) {
                EconomySimulationRuntime.stop(); client.getPlayer().yellowMessage("Economy runtime stopped."); return;
            }
            client.getPlayer().yellowMessage("Usage: !economy start | advance <non-negative-days> | status | stop");
        } catch (RuntimeException failure) {
            client.getPlayer().yellowMessage("Economy command failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    private static void show(Client client, EconomySimulationRuntime.Status status) {
        client.getPlayer().yellowMessage(status.active()
                ? "Economy run=" + status.runId() + " logical=" + status.logicalTime()
                + " admitted=" + status.admittedAgents() + "/" + status.reservedCharacters()
                : "Economy runtime is inactive.");
    }
}
