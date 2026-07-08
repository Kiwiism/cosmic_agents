package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import client.Character;

/**
 * Temporary Agent-owned bridge from Agent runtime status side effects to Agent
 * chat/status runtime action interfaces.
 */
public final class AgentStatusRuntime {
    private AgentStatusRuntime() {
    }

    public static AgentChatStatusRuntime.OfflineReturnActions offlineReturnActions(Character bot) {
        return new AgentChatStatusRuntime.OfflineReturnActions() {
            @Override
            public boolean hasAgent() {
                return bot != null;
            }

            @Override
            public String mapName() {
                return bot != null && bot.getMap() != null ? bot.getMap().getMapName() : null;
            }

            @Override
            public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
                AgentSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                bot.changeFaceExpression(expression);
            }

            @Override
            public void sayParty(String text) {
                AgentReplyRuntime.sayPartyNow(bot, text);
            }
        };
    }

    public static AgentChatStatusRuntime.AfkReturnActions afkReturnActions(AgentRuntimeEntry entry) {
        return new AgentChatStatusRuntime.AfkReturnActions() {
            @Override
            public boolean hasAgent() {
                return AgentRuntimeIdentityRuntime.bot(entry) != null;
            }

            @Override
            public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
                AgentSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                AgentRuntimeIdentityRuntime.bot(entry).changeFaceExpression(expression);
            }

            @Override
            public void reply(String text) {
                AgentReplyRuntime.replyNow(entry, text);
            }
        };
    }
}
