package server.agents.auth;

import client.Character;
import config.YamlConfig;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration-backed administrative authority for Agent commands.
 *
 * <p>Authority is deliberately independent from following, parties, cohorts,
 * trading, and Agent lifecycle identity. Names are resolved from the live
 * character at command ingress; command audit records should continue to use
 * the stable character and account IDs.</p>
 */
public final class AgentAuthorityService {
    private AgentAuthorityService() {
    }

    public static boolean mayObserve(Character actor) {
        return hasRole(actor, AgentAuthorityRole.OBSERVER);
    }

    public static boolean mayOperate(Character actor) {
        return hasRole(actor, AgentAuthorityRole.OPERATOR);
    }

    public static boolean mayAdminister(Character actor) {
        return hasRole(actor, AgentAuthorityRole.ADMINISTRATOR);
    }

    public static boolean hasRole(Character actor, AgentAuthorityRole required) {
        AgentAuthorityRole role = actor == null ? null : roleForName(actor.getName());
        return role != null && role.permits(required);
    }

    public static AgentAuthorityRole roleForName(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return null;
        }
        if (names(YamlConfig.config.server.AGENT_AUTHORITY_ADMINISTRATOR_NAMES).contains(normalized)) {
            return AgentAuthorityRole.ADMINISTRATOR;
        }
        if (names(YamlConfig.config.server.AGENT_AUTHORITY_OPERATOR_NAMES).contains(normalized)) {
            return AgentAuthorityRole.OPERATOR;
        }
        if (names(YamlConfig.config.server.AGENT_AUTHORITY_OBSERVER_NAMES).contains(normalized)) {
            return AgentAuthorityRole.OBSERVER;
        }
        return null;
    }

    public static boolean isTrustedTradePlayer(Character character) {
        return character != null
                && names(YamlConfig.config.server.AGENT_TRUSTED_TRADE_PLAYER_NAMES)
                .contains(normalize(character.getName()));
    }

    private static Set<String> names(String configuredNames) {
        if (configuredNames == null || configuredNames.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredNames.split(","))
                .map(AgentAuthorityService::normalize)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
