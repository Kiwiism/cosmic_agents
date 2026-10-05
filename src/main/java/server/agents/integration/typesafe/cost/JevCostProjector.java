package server.agents.integration.typesafe.cost;

import server.agents.capabilities.dialogue.jev.AgentChatIntentCatalog;
import server.agents.capabilities.partyquest.dialogue.AgentPartyQuestDialogueJudge;
import server.agents.capabilities.trade.jev.AgentOfferReplyJudge;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevCostModel;
import server.agents.integration.typesafe.JevHttpTransport;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.TypeSafeDirectorProposalProvider;
import server.agents.integration.typesafe.TypeSafeSettings;
import server.agents.integration.typesafe.state.JevTokenEstimator;
import server.security.typesafe.JevNameScreen;
import server.security.typesafe.JevReportTriage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Projects TypeSafe spend for an expected bot population before enabling anything.
 *
 * <p>Requests per day are derived from a {@link Scenario}; tokens per request come either from
 * one live measurement per judgment kind ({@code --live}, about seven requests, a fraction of a
 * cent) or from the offline estimator over the same production request builders. The same
 * arithmetic runs inside the {@code !typesafe project} command against the live meter.
 *
 * <pre>
 * java -cp target/Cosmic.jar server.agents.integration.typesafe.cost.JevCostProjector \
 *      --bots 20 --commands-per-bot-hour 6 --hours-online 12 --miss-rate 0.25 --live
 * </pre>
 * Run from the server directory so {@code agent-engine.yaml} is found.
 */
public final class JevCostProjector {
    /** Expected activity. Rates are fractions (0-1); the judge only runs on deterministic misses. */
    public record Scenario(int bots,
                           double commandsPerBotPerHour,
                           double hoursOnlinePerDay,
                           double commandMissRate,
                           double partyQuestSessionsPerDay,
                           double judgedMessagesPerSession,
                           double offersPerDay,
                           double freeFormReplyRate,
                           double newCharactersPerDay,
                           double reportsPerDay,
                           double directorQueriesPerDay) {
        public static Scenario defaults() {
            return new Scenario(20, 6.0d, 12.0d, 0.25d, 6.0d, 15.0d, 40.0d, 0.3d, 5.0d, 3.0d, 20.0d);
        }

        /** Requests per day per kind, in projection order. */
        public Map<String, Double> requestsPerDay() {
            Map<String, Double> requests = new LinkedHashMap<>();
            requests.put(AgentChatIntentCatalog.REQUEST_KIND, bots * commandsPerBotPerHour * hoursOnlinePerDay * commandMissRate);
            requests.put(AgentPartyQuestDialogueJudge.REQUEST_KIND, partyQuestSessionsPerDay * judgedMessagesPerSession);
            requests.put(AgentOfferReplyJudge.REQUEST_KIND, offersPerDay * freeFormReplyRate);
            requests.put(TypeSafeDirectorProposalProvider.SELECT_KIND, directorQueriesPerDay);
            requests.put(TypeSafeDirectorProposalProvider.RANK_KIND, directorQueriesPerDay / 2.0d);
            requests.put(JevNameScreen.REQUEST_KIND, newCharactersPerDay);
            requests.put(JevReportTriage.REQUEST_KIND, reportsPerDay);
            return requests;
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "%d bots x %.1f commands/bot/hour x %.1f h online x %.0f%% regex misses; %.0f PQ sessions x %.0f judged msgs; "
                            + "%.0f offers x %.0f%% free-form replies; %.0f new characters; %.0f reports; %.0f Director queries per day",
                    bots, commandsPerBotPerHour, hoursOnlinePerDay, commandMissRate * 100, partyQuestSessionsPerDay,
                    judgedMessagesPerSession, offersPerDay, freeFormReplyRate * 100, newCharactersPerDay, reportsPerDay,
                    directorQueriesPerDay);
        }
    }

    /** Tokens per request per kind and where the numbers came from. */
    public record Measurement(Map<String, Long> inputTokensPerRequest, boolean live, List<String> notes) {
        public Measurement {
            inputTokensPerRequest = Map.copyOf(new LinkedHashMap<>(inputTokensPerRequest));
            notes = List.copyOf(notes);
        }
    }

    private JevCostProjector() {
    }

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        Scenario scenario = scenario(options);
        boolean live = options.containsKey("live");
        Measurement measurement = live ? measureLive(new JevClient(TypeSafeSettings.runtime(), new JevHttpTransport()))
                : estimateOffline();
        for (String line : render(scenario, measurement)) {
            System.out.println(line);
        }
    }

    /** Sends each sample once and reads the API's own token count. */
    public static Measurement measureLive(JevClient client) {
        Map<String, Long> tokens = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        if (!client.configured()) {
            notes.add("TYPESAFE_API_KEY is not set; falling back to offline estimates");
            Measurement offline = estimateOffline();
            notes.addAll(offline.notes());
            return new Measurement(offline.inputTokensPerRequest(), false, notes);
        }
        for (Map.Entry<String, JevRequest> sample : JevRequestSamples.all().entrySet()) {
            Optional<JevResponse> response = client.ask(sample.getValue());
            if (response.isPresent()) {
                tokens.put(sample.getKey(), response.get().inputTokens());
                notes.add(String.format(Locale.ROOT, "%s: %d input tokens, %d ms", sample.getKey(),
                        response.get().inputTokens(), response.get().latencyMs()));
            } else {
                long estimate = JevTokenEstimator.estimate(sample.getValue().toJson(client.settings().model()));
                tokens.put(sample.getKey(), estimate);
                notes.add(sample.getKey() + ": request failed, using offline estimate " + estimate);
            }
        }
        notes.add("measurement cost: " + JevCostModel.money(client.meter().costUsd()));
        return new Measurement(tokens, true, notes);
    }

    /** No network: serialised request length divided by the configured chars-per-token ratio. */
    public static Measurement estimateOffline() {
        Map<String, Long> tokens = new LinkedHashMap<>();
        for (Map.Entry<String, JevRequest> sample : JevRequestSamples.all().entrySet()) {
            tokens.put(sample.getKey(), (long) JevTokenEstimator.estimate(sample.getValue().toJson(TypeSafeSettings.DEFAULT_MODEL)));
        }
        return new Measurement(tokens, false, List.of(
                "offline estimate from serialised request length; the live measurement (--live) is authoritative"));
    }

    public static JevCostModel.Projection project(Scenario scenario, Map<String, Long> inputTokensPerRequest) {
        Map<String, JevCostModel.Line> lines = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : scenario.requestsPerDay().entrySet()) {
            long tokens = inputTokensPerRequest.getOrDefault(entry.getKey(), 0L);
            lines.put(entry.getKey(), new JevCostModel.Line(entry.getKey(), entry.getValue(), tokens));
        }
        return new JevCostModel.Projection(lines);
    }

    public static List<String> render(Scenario scenario, Measurement measurement) {
        JevCostModel.Projection projection = project(scenario, measurement.inputTokensPerRequest());
        List<String> out = new ArrayList<>();
        out.add("TypeSafe (Jev) cost projection");
        out.add("Scenario: " + scenario.describe());
        out.add("Tokens per request: " + (measurement.live() ? "measured live" : "estimated offline")
                + "; price " + JevCostModel.price(JevCostModel.priceUsdPerMillionInputTokens()) + " per million input tokens, output free");
        out.add(String.format(Locale.ROOT, "%-16s %12s %12s %14s %10s %10s", "kind", "requests/day", "tokens/req", "tokens/day", "usd/day", "usd/month"));
        for (JevCostModel.Line line : projection.lines().values()) {
            out.add(String.format(Locale.ROOT, "%-16s %12.1f %12d %14.0f %10s %10s", line.kind(), line.requestsPerDay(),
                    line.inputTokensPerRequest(), line.tokensPerDay(), JevCostModel.money(line.usdPerDay()),
                    JevCostModel.money(line.usdPerDay() * 30.0d)));
        }
        out.add(String.format(Locale.ROOT, "%-16s %12.1f %12s %14.0f %10s %10s", "TOTAL", projection.requestsPerDay(), "",
                projection.tokensPerDay(), JevCostModel.money(projection.usdPerDay()), JevCostModel.money(projection.usdPerMonth())));
        for (String note : measurement.notes()) {
            out.add("  note: " + note);
        }
        return out;
    }

    static Scenario scenario(Map<String, String> options) {
        Scenario d = Scenario.defaults();
        return new Scenario(
                (int) number(options, "bots", d.bots()),
                number(options, "commands-per-bot-hour", d.commandsPerBotPerHour()),
                number(options, "hours-online", d.hoursOnlinePerDay()),
                number(options, "miss-rate", d.commandMissRate()),
                number(options, "pq-sessions", d.partyQuestSessionsPerDay()),
                number(options, "pq-messages", d.judgedMessagesPerSession()),
                number(options, "offers", d.offersPerDay()),
                number(options, "free-form-rate", d.freeFormReplyRate()),
                number(options, "new-characters", d.newCharactersPerDay()),
                number(options, "reports", d.reportsPerDay()),
                number(options, "director-queries", d.directorQueriesPerDay()));
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            boolean hasValue = index + 1 < args.length && !args[index + 1].startsWith("--");
            options.put(key, hasValue ? args[++index] : "true");
        }
        return options;
    }

    private static double number(Map<String, String> options, String key, double fallback) {
        String value = options.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
