package server.agents.integration;

import server.bots.BotBuildManager;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed AP/SP build state.
 */
public final class AgentBotBuildStateRuntime {
    private AgentBotBuildStateRuntime() {
    }

    public static BotBuildManager.ApBuild apBuild(BotEntry entry) {
        return entry.apBuild();
    }

    public static boolean hasApBuild(BotEntry entry) {
        return entry.apBuild() != null;
    }

    public static void setApBuild(BotEntry entry, BotBuildManager.ApBuild build) {
        entry.setApBuild(build);
    }

    public static void clearApBuildPromptState(BotEntry entry) {
        entry.clearApBuildPromptState();
    }

    public static boolean apPromptSent(BotEntry entry) {
        return entry.apPromptSent();
    }

    public static void markApPromptSent(BotEntry entry) {
        entry.markApPromptSent();
    }

    public static String spVariant(BotEntry entry) {
        return entry.spVariant();
    }

    public static boolean hasSpVariant(BotEntry entry) {
        return entry.spVariant() != null;
    }

    public static void setSpVariant(BotEntry entry, String spVariant) {
        entry.setSpVariant(spVariant);
    }

    public static boolean spVariantPromptSent(BotEntry entry) {
        return entry.spVariantPromptSent();
    }

    public static void markSpVariantPromptSent(BotEntry entry) {
        entry.markSpVariantPromptSent();
    }
}
