package server.agents.integration;

import server.agents.capabilities.build.AgentBuildService;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed AP/SP build state.
 */
public final class AgentBotBuildStateRuntime {
    private AgentBotBuildStateRuntime() {
    }

    public static AgentBuildService.ApBuild apBuild(AgentRuntimeEntry entry) {
        return entry.buildState().apBuild();
    }

    public static boolean hasApBuild(AgentRuntimeEntry entry) {
        return entry.buildState().hasApBuild();
    }

    public static void setApBuild(AgentRuntimeEntry entry, AgentBuildService.ApBuild build) {
        entry.buildState().setApBuild(build);
    }

    public static void clearApBuildPromptState(AgentRuntimeEntry entry) {
        entry.buildState().clearApBuildPromptState();
    }

    public static boolean apPromptSent(AgentRuntimeEntry entry) {
        return entry.buildState().apPromptSent();
    }

    public static void markApPromptSent(AgentRuntimeEntry entry) {
        entry.buildState().markApPromptSent();
    }

    public static String spVariant(AgentRuntimeEntry entry) {
        return entry.buildState().spVariant();
    }

    public static boolean hasSpVariant(AgentRuntimeEntry entry) {
        return entry.buildState().hasSpVariant();
    }

    public static void setSpVariant(AgentRuntimeEntry entry, String spVariant) {
        entry.buildState().setSpVariant(spVariant);
    }

    public static boolean spVariantPromptSent(AgentRuntimeEntry entry) {
        return entry.buildState().spVariantPromptSent();
    }

    public static void markSpVariantPromptSent(AgentRuntimeEntry entry) {
        entry.buildState().markSpVariantPromptSent();
    }

    public static int lastKnownLevel(AgentRuntimeEntry entry) {
        return entry.buildState().lastKnownLevel();
    }

    public static void setLastKnownLevel(AgentRuntimeEntry entry, int level) {
        entry.buildState().setLastKnownLevel(level);
    }

    public static int jobPromptSent(AgentRuntimeEntry entry) {
        return entry.buildState().jobPromptSent();
    }

    public static void setJobPromptSent(AgentRuntimeEntry entry, int level) {
        entry.buildState().setJobPromptSent(level);
    }
}
