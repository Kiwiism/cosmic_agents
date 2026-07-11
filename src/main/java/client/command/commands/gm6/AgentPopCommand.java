package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.population.AgentPopulationAdminService;
import server.agents.population.AgentPopulationMetrics;
import server.agents.population.AgentPopulationPolicy;
import server.agents.population.AgentPopulationRecord;
import server.agents.population.AgentPopulationRuntime;
import server.agents.population.AgentPopulationSnapshot;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** GM6 administrative surface for the external Agent population module. */
public class AgentPopCommand extends Command {
    public AgentPopCommand() {
        setDescription("Manage external Agent population scheduling.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        try {
            AgentPopulationAdminService admin = AgentPopulationRuntime.admin();
            String verb = params.length == 0 ? "status" : params[0].toLowerCase();
            switch (verb) {
                case "status" -> status(player, admin);
                case "on" -> { admin.setEnabled(true); player.yellowMessage("Agent population scheduler ENABLED."); status(player, admin); }
                case "off" -> { admin.setEnabled(false); player.yellowMessage("Agent population scheduler DISABLED."); }
                case "multiplier" -> multiplier(player, admin, params);
                case "list" -> list(player, admin);
                case "sweep" -> {
                    var result = admin.sweep();
                    player.yellowMessage("Population sweep: target=" + result.target() + " live=" + result.liveAfter()
                            + " started=" + result.started() + " stopped=" + result.stopped() + " failed=" + result.failed());
                }
                case "add" -> named(player, params, admin::add, commandPrefix() + " add <agent>");
                case "remove" -> named(player, params, admin::remove, commandPrefix() + " remove <agent>");
                case "crew" -> crew(player, admin, params);
                case "clear" -> player.yellowMessage("Stopped " + admin.clearLive() + " managed Agent session(s). Roster retained.");
                case "wipe" -> wipe(player, admin, params);
                default -> player.yellowMessage("Usage: " + commandPrefix()
                        + " status|on|off|multiplier <n>|list|sweep|add|remove|crew|clear|wipe");
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            player.yellowMessage("Agent population operation failed: " + failure.getMessage());
        }
    }

    private static void status(Character player, AgentPopulationAdminService admin) {
        AgentPopulationSnapshot state = admin.snapshot();
        AgentPopulationMetrics.Snapshot metrics = admin.metrics();
        player.yellowMessage("Agent population: " + (state.enabled() ? "ON" : "OFF")
                + " multiplier=" + state.multiplier() + "x");
        player.yellowMessage("target=" + metrics.target() + " live=" + metrics.live() + " managed=" + state.agents().size()
                + " failures=" + metrics.failures() + " lagMs=" + metrics.reconciliationLagMs()
                + " queued=" + metrics.queuedCallbacks());
    }

    private void multiplier(Character player, AgentPopulationAdminService admin, String[] params) throws IOException {
        if (params.length < 2) { player.yellowMessage("Usage: " + commandPrefix() + " multiplier <nonnegative value>"); return; }
        double value = Double.parseDouble(params[1]);
        admin.setMultiplier(value);
        player.yellowMessage("Agent population multiplier set to " + value + "x.");
    }

    private static void list(Character player, AgentPopulationAdminService admin) {
        List<AgentPopulationRecord> records = admin.snapshot().agents();
        player.yellowMessage("Managed Agents (" + records.size() + "):");
        printBounded(player, records.stream().map(record -> record.name() + " (#" + record.characterId() + ")"
                + (record.crewId() == null ? "" : " crew=" + record.crewId())).toList());
    }

    private void crew(Character player, AgentPopulationAdminService admin, String[] params) throws IOException {
        if (params.length < 3) { player.yellowMessage("Usage: " + commandPrefix() + " crew <id|none> <agents...>"); return; }
        Integer crewId = params[1].equalsIgnoreCase("none") ? null : Integer.valueOf(params[1]);
        int changed = admin.assignCrew(crewId, Arrays.asList(params).subList(2, params.length));
        player.yellowMessage("Updated crew assignment for " + changed + " managed Agent(s).");
    }

    private void wipe(Character player, AgentPopulationAdminService admin, String[] params) throws IOException {
        boolean confirm = params.length >= 2 && params[1].equalsIgnoreCase("confirm");
        if (!confirm) {
            player.yellowMessage("Population roster wipe preview (backing characters will NOT be deleted):");
            printBounded(player, admin.wipePreview());
            player.yellowMessage("Run: " + commandPrefix() + " wipe confirm");
            return;
        }
        AgentPopulationAdminService.WipeResult result = admin.wipeConfirm();
        printBounded(player, result.messages());
    }

    private static void named(Character player, String[] params, NamedOperation operation, String usage) throws IOException {
        if (params.length < 2) { player.yellowMessage("Usage: " + usage); return; }
        player.yellowMessage(operation.apply(params[1]));
    }

    private static void printBounded(Character player, List<String> lines) {
        int shown = Math.min(lines.size(), AgentPopulationPolicy.MAX_LIST_LINES);
        for (int i = 0; i < shown; i++) player.yellowMessage("  " + lines.get(i));
        if (lines.size() > shown) player.yellowMessage("  ... and " + (lines.size() - shown) + " more.");
    }

    @FunctionalInterface private interface NamedOperation { String apply(String name) throws IOException; }

    protected String commandPrefix() {
        return "@agentpop";
    }
}
