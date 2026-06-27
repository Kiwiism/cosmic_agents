package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatReportRuntime;

/**
 * Agent-owned report scheduler adapter. Report orchestration should depend on
 * this narrow boundary instead of the broad delayed-callback runtime.
 */
public final class AgentBotReportSchedulerRuntime {
    private AgentBotReportSchedulerRuntime() {
    }

    public static AgentChatReportRuntime.ReportScheduler reportScheduler() {
        return AgentBotSchedulerRuntime::afterRandomDelay;
    }
}
