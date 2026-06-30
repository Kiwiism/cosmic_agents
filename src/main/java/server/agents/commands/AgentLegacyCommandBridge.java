package server.agents.commands;

import server.agents.runtime.AgentPerformanceMonitor;

import client.Character;
import server.bots.BotManager;
import server.bots.BotNavigationDebugOverlay;
import server.agents.capabilities.dialogue.llm.AgentLlmConfig;

import java.util.List;

/**
 * Transitional command boundary for legacy bot command surfaces while runtime logic is reconstructed
 * into Agent modules.
 */
public final class AgentLegacyCommandBridge {
    private AgentLegacyCommandBridge() {
    }

    public static List<String> combatConfigLines() {
        return BotManager.botCombatConfigLines();
    }

    public static String combatConfigLine(String name) {
        return BotManager.botCombatConfigLine(name);
    }

    public static String setCombatConfig(String name, String value) {
        return BotManager.setBotCombatConfig(name, value);
    }

    public static boolean llmEnabled() {
        return AgentLlmConfig.enabled;
    }

    public static boolean llmDebugLog() {
        return AgentLlmConfig.debugLog;
    }

    public static void setLlm(boolean enabled, boolean debugLog) {
        AgentLlmConfig.enabled = enabled;
        AgentLlmConfig.debugLog = debugLog;
    }

    public static String showNavigationGraph(Character player) {
        return BotNavigationDebugOverlay.showGraph(player);
    }

    public static String showNavigationPath(Character player, String agentName) {
        return BotNavigationDebugOverlay.showPath(player, agentName);
    }

    public static String writeNavigationPathLog(Character player, String agentName, String note) {
        return BotNavigationDebugOverlay.pathLog(player, agentName, note);
    }

    public static String clearNavigationOverlay(Character player) {
        return BotNavigationDebugOverlay.clear(player);
    }

    public static boolean togglePerformanceMonitor() {
        return AgentPerformanceMonitor.toggleEnabled();
    }

    public static void setPerformanceMonitorEnabled(boolean enabled) {
        AgentPerformanceMonitor.setEnabled(enabled);
    }
}
