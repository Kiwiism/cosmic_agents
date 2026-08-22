package server.agents.commands;

import server.agents.monitoring.AgentPerformanceMonitor;

import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.social.contracts.DialogueMode;
import server.agents.social.conversation.AgentSocialDialogueRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.List;

/** Routes retained legacy command aliases into Agent-owned modules. */
public final class AgentLegacyCommandBridge {
    private static final AtomicBoolean dialogueDebug = new AtomicBoolean();
    private AgentLegacyCommandBridge() {
    }

    public static List<String> combatConfigLines() {
        return AgentCombatConfig.configFieldLines();
    }

    public static String combatConfigLine(String name) {
        return AgentCombatConfig.configFieldLine(name);
    }

    public static String setCombatConfig(String name, String value) {
        return AgentCombatConfig.setConfigField(name, value);
    }

    public static boolean llmEnabled() {
        return AgentSocialDialogueRuntime.mode() == DialogueMode.DIALOGUE_ONLY;
    }

    public static boolean llmDebugLog() {
        return dialogueDebug.get();
    }

    public static void setLlm(boolean enabled, boolean debugLog) {
        AgentSocialDialogueRuntime.setMode(
                enabled ? DialogueMode.DIALOGUE_ONLY : DialogueMode.DETERMINISTIC_ONLY);
        dialogueDebug.set(debugLog);
    }

    public static boolean togglePerformanceMonitor() {
        return AgentPerformanceMonitor.toggleEnabled();
    }

    public static void setPerformanceMonitorEnabled(boolean enabled) {
        AgentPerformanceMonitor.setEnabled(enabled);
    }
}
