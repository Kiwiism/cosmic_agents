package server.agents.integration;

import server.agents.capabilities.build.AgentBuildService;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed AP/SP build state.
 */
public final class AgentBotBuildStateRuntime {
    private AgentBotBuildStateRuntime() {
    }

    public static AgentBuildService.ApBuild apBuild(BotEntry entry) {
        return entry.buildState().apBuild();
    }

    public static boolean hasApBuild(BotEntry entry) {
        return entry.buildState().hasApBuild();
    }

    public static void setApBuild(BotEntry entry, AgentBuildService.ApBuild build) {
        entry.buildState().setApBuild(build);
    }

    public static void clearApBuildPromptState(BotEntry entry) {
        entry.buildState().clearApBuildPromptState();
    }

    public static boolean apPromptSent(BotEntry entry) {
        return entry.buildState().apPromptSent();
    }

    public static void markApPromptSent(BotEntry entry) {
        entry.buildState().markApPromptSent();
    }

    public static String spVariant(BotEntry entry) {
        return entry.buildState().spVariant();
    }

    public static boolean hasSpVariant(BotEntry entry) {
        return entry.buildState().hasSpVariant();
    }

    public static void setSpVariant(BotEntry entry, String spVariant) {
        entry.buildState().setSpVariant(spVariant);
    }

    public static boolean spVariantPromptSent(BotEntry entry) {
        return entry.buildState().spVariantPromptSent();
    }

    public static void markSpVariantPromptSent(BotEntry entry) {
        entry.buildState().markSpVariantPromptSent();
    }

    public static int lastKnownLevel(BotEntry entry) {
        return entry.buildState().lastKnownLevel();
    }

    public static void setLastKnownLevel(BotEntry entry, int level) {
        entry.buildState().setLastKnownLevel(level);
    }

    public static int jobPromptSent(BotEntry entry) {
        return entry.buildState().jobPromptSent();
    }

    public static void setJobPromptSent(BotEntry entry, int level) {
        entry.buildState().setJobPromptSent(level);
    }
}
