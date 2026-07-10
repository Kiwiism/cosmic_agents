package server.agents.runtime;

import server.agents.capabilities.follow.AgentActivityStateRuntime;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.capabilities.trade.AgentOfferStateRuntime;

import java.awt.Point;

/**
 * Runtime-owned adapter from live Agent session state to dialogue status state
 * interfaces.
 */
public final class AgentStatusStateRuntime {
    private AgentStatusStateRuntime() {
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
}
