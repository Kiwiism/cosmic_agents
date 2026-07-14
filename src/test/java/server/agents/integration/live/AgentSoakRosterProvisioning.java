package server.agents.integration.live;

import server.agents.population.AgentPopulationRecord;
import server.agents.population.AgentPopulationSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure validation and deterministic naming for the opt-in soak roster tool. */
final class AgentSoakRosterProvisioning {
    static final int MAX_TARGET = 2_000;
    static final String CONFIRMATION = "PROVISION_DISPOSABLE_AGENT_ROSTER";
    private static final Pattern DATABASE_PATTERN = Pattern.compile(
            "(?im)^\\s*DB_URL_FORMAT\\s*:\\s*[\"'](?<url>[^\"']+)[\"']");
    private static final Pattern JDBC_DATABASE_PATTERN = Pattern.compile(
            "^jdbc:mysql://[^/]+/(?<database>[^?;/]+)(?:[?;].*)?$");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,7}");

    private AgentSoakRosterProvisioning() {
    }

    static Options parse(String[] args, Path repoRoot) {
        int target = -1;
        String prefix = "Sched";
        String expectedDatabase = null;
        String confirmation = null;
        Path output = null;
        for (String arg : args) {
            if (arg.startsWith("--target=")) {
                target = parseTarget(arg.substring("--target=".length()));
            } else if (arg.startsWith("--prefix=")) {
                prefix = arg.substring("--prefix=".length());
            } else if (arg.startsWith("--expected-database=")) {
                expectedDatabase = arg.substring("--expected-database=".length());
            } else if (arg.startsWith("--output=")) {
                output = Path.of(arg.substring("--output=".length()));
            } else if (arg.startsWith("--confirm=")) {
                confirmation = arg.substring("--confirm=".length());
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (target < 1 || target > MAX_TARGET) {
            throw new IllegalArgumentException("--target must be between 1 and " + MAX_TARGET);
        }
        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new IllegalArgumentException("--prefix must be 1-8 ASCII letters/digits and start with a letter");
        }
        if (expectedDatabase == null || expectedDatabase.isBlank()) {
            throw new IllegalArgumentException("--expected-database is required");
        }
        requireDisposableDatabase(expectedDatabase, expectedDatabase);
        if (output == null) {
            throw new IllegalArgumentException("--output is required");
        }
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (isSameOrChild(normalizedOutput, normalizedRoot)) {
            throw new IllegalArgumentException("--output must be outside the worktree");
        }
        if (!CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("--confirm=" + CONFIRMATION + " is required");
        }
        if (name(prefix, target).length() > 12) {
            throw new IllegalArgumentException("--prefix is too long for the target's 12-character names");
        }
        return new Options(target, prefix, expectedDatabase, normalizedOutput);
    }

    static String configuredDatabase(String configText) {
        Matcher configMatcher = DATABASE_PATTERN.matcher(Objects.requireNonNull(configText, "configText"));
        if (!configMatcher.find()) {
            throw new IllegalArgumentException("config.yaml does not contain DB_URL_FORMAT");
        }
        Matcher jdbcMatcher = JDBC_DATABASE_PATTERN.matcher(configMatcher.group("url"));
        if (!jdbcMatcher.matches()) {
            throw new IllegalArgumentException("DB_URL_FORMAT is not a supported MySQL JDBC URL");
        }
        return jdbcMatcher.group("database");
    }

    static void requireDisposableDatabase(String configuredDatabase, String expectedDatabase) {
        if (!configuredDatabase.equals(expectedDatabase)) {
            throw new IllegalArgumentException("Configured database '" + configuredDatabase
                    + "' does not match expected database '" + expectedDatabase + "'");
        }
        String lower = configuredDatabase.toLowerCase(Locale.ROOT);
        if (lower.equals("cosmic") || !lower.startsWith("cosmic_scheduler_soak_")) {
            throw new IllegalArgumentException(
                    "Database must use the cosmic_scheduler_soak_ disposable naming prefix");
        }
    }

    static String name(String prefix, int sequence) {
        if (sequence < 1 || sequence > MAX_TARGET) {
            throw new IllegalArgumentException("sequence must be between 1 and " + MAX_TARGET);
        }
        return "%s%04d".formatted(prefix, sequence);
    }

    static AgentPopulationSnapshot snapshot(List<AgentPopulationRecord> records) {
        return new AgentPopulationSnapshot(true, 1.0, new ArrayList<>(records));
    }

    private static int parseTarget(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--target must be an integer", failure);
        }
    }

    private static boolean isSameOrChild(Path candidate, Path root) {
        String candidateText = candidate.toString();
        String rootText = root.toString();
        return candidateText.equalsIgnoreCase(rootText)
                || candidateText.toLowerCase(Locale.ROOT).startsWith(
                rootText.toLowerCase(Locale.ROOT) + root.getFileSystem().getSeparator());
    }

    record Options(int target, String prefix, String expectedDatabase, Path output) {
    }
}
