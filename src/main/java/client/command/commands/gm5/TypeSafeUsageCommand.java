package client.command.commands.gm5;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevCostModel;
import server.agents.integration.typesafe.JevUsageMeter;
import server.agents.integration.typesafe.JevUsageReporter;
import server.agents.integration.typesafe.cost.JevCostProjector;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code !typesafe} shows live TypeSafe usage and spend; {@code !typesafe report} also writes the
 * usage log and CSV row now; {@code !typesafe project <bots> [commands/bot/hour] [hours online]}
 * projects daily and monthly cost for a bot population using tokens-per-request measured so far.
 */
public class TypeSafeUsageCommand extends Command {
    {
        setDescription("TypeSafe (Jev) usage, spend and cost projection. Usage: !typesafe [report | project <bots> [cmds/bot/h] [hours]]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        JevClient client = JevClient.runtime();
        if (params.length > 0 && params[0].equalsIgnoreCase("project")) {
            project(player, client, params);
            return;
        }
        player.dropMessage(6, "TypeSafe: " + (client.configured() ? "configured" : "no TYPESAFE_API_KEY")
                + ", model " + client.settings().model() + ", breaker " + (client.available() || !client.configured() ? "closed" : "OPEN"));
        for (String line : client.meter().report(System.currentTimeMillis())) {
            player.dropMessage(6, line);
        }
        if (params.length > 0 && params[0].equalsIgnoreCase("report")) {
            JevUsageReporter.report();
            player.dropMessage(6, "Written to the server log and " + JevUsageReporter.CSV_PATH);
        }
    }

    private static void project(Character player, JevClient client, String[] params) {
        JevCostProjector.Scenario defaults = JevCostProjector.Scenario.defaults();
        int bots = params.length > 1 ? parseInt(params[1], defaults.bots()) : defaults.bots();
        double commands = params.length > 2 ? parseDouble(params[2], defaults.commandsPerBotPerHour()) : defaults.commandsPerBotPerHour();
        double hours = params.length > 3 ? parseDouble(params[3], defaults.hoursOnlinePerDay()) : defaults.hoursOnlinePerDay();
        JevCostProjector.Scenario scenario = new JevCostProjector.Scenario(bots, commands, hours,
                defaults.commandMissRate(), defaults.partyQuestSessionsPerDay(), defaults.judgedMessagesPerSession(),
                defaults.offersPerDay(), defaults.freeFormReplyRate(), defaults.newCharactersPerDay(),
                defaults.reportsPerDay(), defaults.directorQueriesPerDay());

        // Tokens per request: what this server has actually measured, else the offline estimate.
        Map<String, Long> tokens = new LinkedHashMap<>(JevCostProjector.estimateOffline().inputTokensPerRequest());
        int measuredKinds = 0;
        for (JevUsageMeter.KindUsage kind : client.meter().kinds()) {
            if (kind.averageInputTokens() > 0) {
                tokens.put(kind.kind(), kind.averageInputTokens());
                measuredKinds++;
            }
        }
        JevCostModel.Projection projection = JevCostProjector.project(scenario, tokens);
        player.dropMessage(6, String.format(Locale.ROOT, "Projection for %d bots, %.1f cmds/bot/h, %.1f h/day (%d kinds from live measurements, rest estimated):",
                bots, commands, hours, measuredKinds));
        for (JevCostModel.Line line : projection.lines().values()) {
            if (line.requestsPerDay() <= 0) {
                continue;
            }
            player.dropMessage(6, String.format(Locale.ROOT, "  %s: %.0f req/day x %d tok = %s/day",
                    line.kind(), line.requestsPerDay(), line.inputTokensPerRequest(), JevCostModel.money(line.usdPerDay())));
        }
        player.dropMessage(6, String.format(Locale.ROOT, "  TOTAL %.0f req/day, %.0f tokens/day = %s/day, %s/month",
                projection.requestsPerDay(), projection.tokensPerDay(), JevCostModel.money(projection.usdPerDay()),
                JevCostModel.money(projection.usdPerMonth())));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
