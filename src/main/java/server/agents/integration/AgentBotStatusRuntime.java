package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.bots.BotEntry;
import client.Character;

import java.awt.Point;

/**
 * Temporary Agent-owned bridge from legacy BotEntry status fields to Agent
 * chat/status runtime state interfaces.
 */
public final class AgentBotStatusRuntime {
    private AgentBotStatusRuntime() {
    }

    public static AgentChatStatusRuntime.StatusState statusState(BotEntry entry) {
        return new AgentChatStatusRuntime.StatusState() {
            @Override
            public void setOwnerAfkPosition(Point position) {
                AgentBotActivityStateRuntime.setOwnerAfkPosition(entry, position);
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                AgentBotActivityStateRuntime.setOwnerAfkSinceMs(entry, sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return AgentBotActivityStateRuntime.ownerWasAfk(entry);
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                AgentBotActivityStateRuntime.setOwnerWasAfk(entry, wasAfk);
            }
        };
    }

    public static AgentChatWelcomeBackFlow.AfkState afkState(BotEntry entry) {
        return new AgentChatWelcomeBackFlow.AfkState() {
            @Override
            public Point ownerAfkPosition() {
                return AgentBotActivityStateRuntime.ownerAfkPosition(entry);
            }

            @Override
            public void setOwnerAfkPosition(Point position) {
                AgentBotActivityStateRuntime.setOwnerAfkPosition(entry, position);
            }

            @Override
            public long ownerAfkSinceMs() {
                return AgentBotActivityStateRuntime.ownerAfkSinceMs(entry);
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                AgentBotActivityStateRuntime.setOwnerAfkSinceMs(entry, sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return AgentBotActivityStateRuntime.ownerWasAfk(entry);
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                AgentBotActivityStateRuntime.setOwnerWasAfk(entry, wasAfk);
            }
        };
    }

    public static AgentChatStatusRuntime.StatusCheckState statusCheckState(BotEntry entry) {
        return new AgentChatStatusRuntime.StatusCheckState() {
            @Override
            public boolean spawnUpgradeCheckDone() {
                return entry.upgradeOfferState().spawnUpgradeCheckDone();
            }

            @Override
            public void setSpawnUpgradeCheckDone(boolean done) {
                entry.upgradeOfferState().setSpawnUpgradeCheckDone(done);
            }
        };
    }

    public static AgentChatStatusRuntime.GearSuggestionState gearSuggestionState(BotEntry entry) {
        return new AgentChatStatusRuntime.GearSuggestionState() {
            @Override
            public long nextGearSuggestionAt() {
                return entry.upgradeOfferState().nextGearSuggestionAt();
            }

            @Override
            public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
                entry.upgradeOfferState().setNextGearSuggestionAt(nextGearSuggestionAt);
            }
        };
    }

    public static AgentChatReportRuntime.RecommendedGearState recommendedGearReportState(BotEntry entry) {
        return nextGearSuggestionAt -> entry.upgradeOfferState().setNextGearSuggestionAt(nextGearSuggestionAt);
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
                AgentBotStatusSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                bot.changeFaceExpression(expression);
            }

            @Override
            public void sayParty(String text) {
                AgentBotStatusReplyRuntime.sayPartyNow(bot, text);
            }
        };
    }

    public static AgentChatStatusRuntime.AfkReturnActions afkReturnActions(BotEntry entry) {
        return new AgentChatStatusRuntime.AfkReturnActions() {
            @Override
            public boolean hasAgent() {
                return entry.bot() != null;
            }

            @Override
            public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
                AgentBotStatusSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                entry.bot().changeFaceExpression(expression);
            }

            @Override
            public void reply(String text) {
                AgentBotStatusReplyRuntime.replyNow(entry, text);
            }
        };
    }
}
