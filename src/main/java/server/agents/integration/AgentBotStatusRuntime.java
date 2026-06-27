package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.bots.BotEntry;

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
                entry.setOwnerAfkPosition(position);
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                entry.setOwnerAfkSinceMs(sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return entry.ownerWasAfk();
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                entry.setOwnerWasAfk(wasAfk);
            }
        };
    }

    public static AgentChatWelcomeBackFlow.AfkState afkState(BotEntry entry) {
        return new AgentChatWelcomeBackFlow.AfkState() {
            @Override
            public Point ownerAfkPosition() {
                return entry.ownerAfkPosition();
            }

            @Override
            public void setOwnerAfkPosition(Point position) {
                entry.setOwnerAfkPosition(position);
            }

            @Override
            public long ownerAfkSinceMs() {
                return entry.ownerAfkSinceMs();
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                entry.setOwnerAfkSinceMs(sinceMs);
            }

            @Override
            public boolean ownerWasAfk() {
                return entry.ownerWasAfk();
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                entry.setOwnerWasAfk(wasAfk);
            }
        };
    }

    public static AgentChatStatusRuntime.StatusCheckState statusCheckState(BotEntry entry) {
        return new AgentChatStatusRuntime.StatusCheckState() {
            @Override
            public boolean spawnUpgradeCheckDone() {
                return entry.spawnUpgradeCheckDone();
            }

            @Override
            public void setSpawnUpgradeCheckDone(boolean done) {
                entry.setSpawnUpgradeCheckDone(done);
            }
        };
    }

    public static AgentChatStatusRuntime.GearSuggestionState gearSuggestionState(BotEntry entry) {
        return new AgentChatStatusRuntime.GearSuggestionState() {
            @Override
            public long nextGearSuggestionAt() {
                return entry.nextGearSuggestionAt();
            }

            @Override
            public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
                entry.setNextGearSuggestionAt(nextGearSuggestionAt);
            }
        };
    }
}
