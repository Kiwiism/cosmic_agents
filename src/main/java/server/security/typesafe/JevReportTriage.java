package server.security.typesafe;

import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.security.SecurityEventRuntime;
import server.security.SecurityEventType;
import server.security.SecuritySeverity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Triages a player report asynchronously: a Score for severity and a Choice for category over the
 * report description and the attached chat log. The report is stored and GMs are notified exactly
 * as before; when a judgment arrives, a second GM notice carries the triage and a security event is
 * recorded so serious reports stand out in the console.
 */
public final class JevReportTriage {
    private static final Logger log = LoggerFactory.getLogger(JevReportTriage.class);
    static final String SEVERITY = "severity";
    static final String CATEGORY = "category";
    public static final String REQUEST_KIND = "report-triage";
    static final List<Object> SEVERITY_LEVELS = List.of(
            "nothing actionable: banter, a misunderstanding, or no evidence in the chat log",
            "minor: rude language or spam that a warning would settle",
            "serious: targeted harassment, hate speech, threats, or sharing personal information",
            "urgent: real-money trading, scamming, or a credible hacking or exploit claim with evidence");

    /** What the triage produced, for the GM notice and the security event. */
    public record Triage(String category, double severityScore, double severityConfidence,
                         String severityLabel, long latencyMs) {
        public String noticeSuffix() {
            return " [triage: " + severityLabel + " / " + category
                    + String.format(Locale.ROOT, ", confidence %.2f]", severityConfidence);
        }
    }

    private JevReportTriage() {
    }

    public static boolean enabled() {
        return YamlConfig.config.server.USE_TYPESAFE_REPORT_TRIAGE;
    }

    /**
     * Fire-and-forget. {@code gmNotice} receives one follow-up line when a judgment arrives.
     * Never blocks the caller; unconfigured or failing judgments produce nothing.
     */
    public static void triageAsync(int reporterId, String reporterName, int victimId, String victimName,
                                   String description, String chatlog, Consumer<String> gmNotice) {
        if (!enabled()) {
            return;
        }
        JevClient client = JevClient.runtime();
        if (!client.available()) {
            return;
        }
        CompletableFuture<Optional<JevResponse>> pending =
                client.askAsync(request(reporterName, victimName, description, chatlog));
        pending.thenAccept(response -> response.flatMap(JevReportTriage::triage).ifPresent(triage -> {
            log.info("[typesafe-report] {} reported {}: {} {}", reporterName, victimName,
                    triage.noticeSuffix().trim(), triage.latencyMs() + "ms");
            gmNotice.accept("Report on " + victimName + " triaged" + triage.noticeSuffix());
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("reporter", reporterName);
            evidence.put("victim", victimName);
            evidence.put("category", triage.category());
            evidence.put("severity", triage.severityLabel());
            evidence.put("severityScore", String.format(Locale.ROOT, "%.2f", triage.severityScore()));
            evidence.put("confidence", String.format(Locale.ROOT, "%.2f", triage.severityConfidence()));
            SecurityEventRuntime.record(victimId, SecurityEventType.PLAYER_REPORT_TRIAGE,
                    severityFor(triage), evidence);
        })).exceptionally(failure -> {
            log.debug("report triage failed", failure);
            return null;
        });
    }

    public static JevRequest request(String reporterName, String victimName, String description, String chatlog) {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reporter", reporterName);
        report.put("reported_player", victimName);
        report.put("reporter_description", description == null ? "" : description.trim());
        state.put("report", report);
        if (chatlog != null && !chatlog.isBlank()) {
            state.put("chat_log", chatlog.trim());
        }
        state.put("server_policy", "Friends-only server. Harassment, hate speech, real-money trading, "
                + "scams and cheating are against the rules; banter between friends is not.");
        Map<String, Object> severityInstructions = new LinkedHashMap<>();
        severityInstructions.put("question", "How serious is the reported behaviour, judged against `server_policy`?");
        severityInstructions.put("inspect", "`report.reporter_description` and `chat_log` (the log is the evidence; the description is only a claim)");
        Map<String, Object> categoryInstructions = new LinkedHashMap<>();
        categoryInstructions.put("question", "Which category best describes what the report is about?");
        categoryInstructions.put("inspect", "`report.reporter_description` and `chat_log`");
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("harassment", "insults, threats, or persistent unwanted attention aimed at a player");
        categories.put("hate_speech", "slurs or hateful content about a group");
        categories.put("rmt_or_advertising", "selling items or accounts for real money, or advertising");
        categories.put("scam", "trying to trick a player out of items or mesos");
        categories.put("cheating", "claims of hacking, botting, or exploiting");
        categories.put("spam", "repeated or flooding messages without other harm");
        categories.put("no_issue", "nothing against the rules is described or shown");
        return JevRequest.builder(state).kind(REQUEST_KIND)
                .score(SEVERITY, severityInstructions, SEVERITY_LEVELS)
                .choice(CATEGORY, categoryInstructions, categories)
                .build();
    }

    static Optional<Triage> triage(JevResponse response) {
        Optional<JevAnswer> severity = response.answer(SEVERITY);
        Optional<JevAnswer> category = response.answer(CATEGORY);
        if (severity.isEmpty() || category.isEmpty()) {
            return Optional.empty();
        }
        String label = severity.get().mostLikelyOption()
                .map(option -> severity.get().legend().getOrDefault(option, option))
                .map(text -> text.contains(":") ? text.substring(0, text.indexOf(':')) : text)
                .orElse("unrated");
        return Optional.of(new Triage(
                category.get().mostLikelyOption().orElse("unknown"),
                severity.get().scoreOrZero(),
                severity.get().confidenceOrZero(),
                label,
                response.latencyMs()));
    }

    static SecuritySeverity severityFor(Triage triage) {
        String label = triage.severityLabel().toLowerCase(Locale.ROOT);
        if (label.startsWith("urgent")) {
            return SecuritySeverity.CRITICAL;
        }
        if (label.startsWith("serious")) {
            return SecuritySeverity.WARNING;
        }
        return SecuritySeverity.INFO;
    }
}
