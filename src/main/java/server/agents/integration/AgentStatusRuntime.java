package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.runtime.AgentRuntimeEntry;
import client.Character;

import java.awt.Point;

/**
 * Temporary Agent-owned bridge from Agent runtime status fields to Agent
 * chat/status runtime state interfaces.
 */
public final class AgentStatusRuntime {
    private AgentStatusRuntime() {
    }

    public static AgentChatStatusRuntime.StatusState statusState(AgentRuntimeEntry entry) {
        return new AgentChatStatusRuntime.StatusState() {
            @Override
            public void setOwnerAfkPosition(Point position) {
                AgentActivityStateRuntime.setOwnerAfkPosition(entry, position);
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                AgentActivityStateRuntime.setOwnerAfkSinceMs(entry, sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return AgentActivityStateRuntime.ownerWasAfk(entry);
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                AgentActivityStateRuntime.setOwnerWasAfk(entry, wasAfk);
            }
        };
    }

    public static AgentChatWelcomeBackFlow.AfkState afkState(AgentRuntimeEntry entry) {
        return new AgentChatWelcomeBackFlow.AfkState() {
            @Override
            public Point ownerAfkPosition() {
                return AgentActivityStateRuntime.ownerAfkPosition(entry);
            }

            @Override
            public void setOwnerAfkPosition(Point position) {
                AgentActivityStateRuntime.setOwnerAfkPosition(entry, position);
            }

            @Override
            public long ownerAfkSinceMs() {
                return AgentActivityStateRuntime.ownerAfkSinceMs(entry);
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                AgentActivityStateRuntime.setOwnerAfkSinceMs(entry, sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return AgentActivityStateRuntime.ownerWasAfk(entry);
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                AgentActivityStateRuntime.setOwnerWasAfk(entry, wasAfk);
            }
        };
    }

    public static AgentChatStatusRuntime.StatusCheckState statusCheckState(AgentRuntimeEntry entry) {
        return new AgentChatStatusRuntime.StatusCheckState() {
            @Override
            public boolean spawnUpgradeCheckDone() {
                return AgentOfferStateRuntime.spawnUpgradeCheckDone(entry);
            }

            @Override
            public void setSpawnUpgradeCheckDone(boolean done) {
                AgentOfferStateRuntime.setSpawnUpgradeCheckDone(entry, done);
            }
        };
    }

    public static AgentChatStatusRuntime.GearSuggestionState gearSuggestionState(AgentRuntimeEntry entry) {
        return new AgentChatStatusRuntime.GearSuggestionState() {
            @Override
            public long nextGearSuggestionAt() {
                return AgentOfferStateRuntime.nextGearSuggestionAt(entry);
            }

            @Override
            public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
                AgentOfferStateRuntime.setNextGearSuggestionAt(entry, nextGearSuggestionAt);
            }
        };
    }

    public static AgentChatReportRuntime.RecommendedGearState recommendedGearReportState(AgentRuntimeEntry entry) {
        return nextGearSuggestionAt -> AgentOfferStateRuntime.setNextGearSuggestionAt(entry, nextGearSuggestionAt);
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
