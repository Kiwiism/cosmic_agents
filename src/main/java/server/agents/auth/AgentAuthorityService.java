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
    /** An external grant of operator authority: true when the actor may command its own companions. */
    @FunctionalInterface
    public interface OperatorGrant {
        boolean permitsOperate(Character actor);
    }

    private static volatile OperatorGrant operatorGrant;

    private AgentAuthorityService() {
    }

    /**
     * Installs a grant consulted after the name allowlists, e.g. a player who has recruited a
     * companion through a contract. Command routing still only reaches the actor's own Agents.
     */
    public static void installOperatorGrant(OperatorGrant grant) {
        operatorGrant = grant;
    }

    public static boolean mayObserve(Character actor) {
        return hasRole(actor, AgentAuthorityRole.OBSERVER);
    }

    public static boolean mayOperate(Character actor) {
        if (hasRole(actor, AgentAuthorityRole.OPERATOR)) {
            return true;
        }
        OperatorGrant grant = operatorGrant;
        return grant != null && actor != null && grant.permitsOperate(actor);
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
        if (names(config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_ADMINISTRATOR_NAMES).contains(normalized)) {
            return AgentAuthorityRole.ADMINISTRATOR;
        }
        if (names(config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_OPERATOR_NAMES).contains(normalized)) {
            return AgentAuthorityRole.OPERATOR;
        }
        if (names(config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_OBSERVER_NAMES).contains(normalized)) {
            return AgentAuthorityRole.OBSERVER;
        }
        return null;
    }

    public static boolean isTrustedTradePlayer(Character character) {
        return character != null
                && names(config.AgentYamlConfig.config.agent.AGENT_TRUSTED_TRADE_PLAYER_NAMES)
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
