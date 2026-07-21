package server.agents.capabilities.dialogue;

import config.YamlConfig;
import server.agents.capabilities.dialogue.conversation.AgentConversationRuntime;
import server.agents.capabilities.dialogue.conversation.AgentConversationSessionView;
import server.agents.capabilities.dialogue.conversation.AgentConversationTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentDialogueRuntimeSnapshot;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicDefinition;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Compact operator view and reversible live controls for Agent dialogue. */
public final class AgentDialogueDiagnostics {
    private static final int MAX_SESSION_LINES = 12;

    private AgentDialogueDiagnostics() {
    }

    public static List<String> lines(String[] params) {
        AgentConversationTopicRegistry.ensureLoaded();
        if (params == null || params.length == 0 || "status".equalsIgnoreCase(params[0])) {
            return statusLines();
        }
        return switch (params[0].toLowerCase(Locale.ROOT)) {
            case "topics" -> topicLines();
            case "topic" -> updateTopic(params);
            case "sessions" -> sessionLines();
            case "metrics" -> metricLines();
            default -> usageLines();
        };
    }

    static List<String> statusLines() {
        AgentDialogueRuntimeSnapshot snapshot = AgentConversationRuntime.snapshot();
        return List.of(
                "Agent dialogue: enabled=" + AgentDialogueTopicRegistry.systemEnabled()
                        + " conversations=" + YamlConfig.config.server.AGENT_CONVERSATION_ENABLED
                        + " simulateUnobserved="
                        + YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED,
                "Dialogue activity: semanticActs=" + snapshot.semanticActs()
                        + " projected=" + snapshot.projected()
                        + " activeSessions=" + snapshot.activeSessions()
                        + " sessions=" + snapshot.sessionsStarted() + "/"
                        + snapshot.sessionsCompleted() + "/" + snapshot.sessionsTimedOut(),
                "Dialogue budgets: mapIntervalMs="
                        + YamlConfig.config.server.AGENT_DIALOGUE_MAP_MESSAGE_INTERVAL_MS
                        + " tickIntervalMs="
                        + YamlConfig.config.server.AGENT_CONVERSATION_TICK_INTERVAL_MS
                        + " maxVisibleSessionsPerMap="
                        + YamlConfig.config.server.AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP
                        + " maxTurns=" + YamlConfig.config.server.AGENT_CONVERSATION_MAX_TURNS,
                "Use !agentchat topics|sessions|metrics or !agentchat topic <id> on|off|default");
    }

    static List<String> topicLines() {
        List<AgentDialogueTopicDefinition> topics = AgentDialogueTopicRegistry.definitions().values().stream()
                .sorted(Comparator.comparing(AgentDialogueTopicDefinition::topicId))
                .toList();
        List<String> lines = new ArrayList<>(topics.size() + 1);
        lines.add("Agent dialogue topics (effective/configured/override):");
        for (AgentDialogueTopicDefinition topic : topics) {
            Boolean override = AgentDialogueTopicRegistry.liveOverride(topic.topicId());
            lines.add("  " + topic.topicId() + "=" + AgentDialogueTopicRegistry.enabled(topic.topicId())
                    + "/" + AgentDialogueTopicRegistry.configuredEnabled(topic.topicId())
                    + "/" + (override == null ? "default" : override)
                    + " - " + topic.description());
        }
        return List.copyOf(lines);
    }

    static List<String> sessionLines() {
        List<AgentConversationSessionView> sessions = AgentConversationRuntime.sessionsSnapshot();
        if (sessions.isEmpty()) {
            return List.of("Agent conversations: no active sessions");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Agent conversations: active=" + sessions.size());
        int shown = 0;
        for (AgentConversationSessionView session : sessions) {
            if (shown++ == MAX_SESSION_LINES) {
                lines.add("  ... " + (sessions.size() - MAX_SESSION_LINES) + " more session(s)");
                break;
            }
            lines.add("  id=" + session.conversationId() + " agents=" + session.firstAgentId()
                    + "/" + session.secondAgentId() + " map=" + session.mapId()
                    + " topic=" + session.topicId() + " turns=" + session.completedTurns()
                    + "/" + session.maxTurns());
        }
        return List.copyOf(lines);
    }

    static List<String> metricLines() {
        AgentDialogueRuntimeSnapshot value = AgentConversationRuntime.snapshot();
        return List.of(
                "Dialogue projection: acts=" + value.semanticActs()
                        + " requests=" + value.projectionRequests()
                        + " projected=" + value.projected(),
                "Dialogue suppression: topic=" + value.topicSuppressed()
                        + " noAudience=" + value.noAudienceSuppressed()
                        + " cooldown=" + value.cooldownSuppressed()
                        + " mapBudget=" + value.mapBudgetSuppressed(),
                "Dialogue sessions: active=" + value.activeSessions()
                        + " started=" + value.sessionsStarted()
                        + " completed=" + value.sessionsCompleted()
                        + " timedOut=" + value.sessionsTimedOut(),
                "Dialogue coordination: published=" + value.coordinationPublished()
                        + " delivered=" + value.coordinationDelivered()
                        + " failures=" + value.failures());
    }

    private static List<String> updateTopic(String[] params) {
        if (params.length < 3) {
            return List.of("Usage: !agentchat topic <id> on|off|default");
        }
        Boolean value = switch (params[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "1" -> Boolean.TRUE;
            case "off", "false", "0" -> Boolean.FALSE;
            case "default", "reset" -> null;
            default -> throw new IllegalArgumentException("Expected on, off, or default");
        };
        AgentDialogueTopicRegistry.setOverride(params[1], value);
        return List.of("Agent dialogue topic " + params[1] + " now "
                + AgentDialogueTopicRegistry.enabled(params[1])
                + (value == null ? " (config default)" : " (live override)"));
    }

    private static List<String> usageLines() {
        return List.of("Usage: !agentchat status|topics|sessions|metrics",
                "       !agentchat topic <id> on|off|default");
    }
}
