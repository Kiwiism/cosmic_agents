package server.agents.capabilities.quest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record AmherstTestResetConfig(boolean enabled,
                                     Set<Integer> allowedCharacterIds,
                                     Set<String> allowedCharacterNames) {
    public AmherstTestResetConfig {
        allowedCharacterIds = allowedCharacterIds == null ? Set.of() : Set.copyOf(allowedCharacterIds);
        allowedCharacterNames = allowedCharacterNames == null ? Set.of() : Set.copyOf(allowedCharacterNames);
    }

    public static AmherstTestResetConfig fromSystemProperties() {
        return new AmherstTestResetConfig(
                Boolean.getBoolean("agents.amherst.reset.enabled"),
                parseIds(System.getProperty("agents.amherst.reset.characterIds", "")),
                parseNames(System.getProperty("agents.amherst.reset.characterNames", "")));
    }

    static Set<Integer> parseIds(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(AmherstTestResetConfig::parsePositiveId)
                .filter(id -> id != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> parseNames(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Integer parsePositiveId(String token) {
        try {
            int id = Integer.parseInt(token);
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
