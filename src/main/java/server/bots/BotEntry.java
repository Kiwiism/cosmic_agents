package server.bots;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ScheduledFuture;

/**
 * Temporary compatibility shell. Runtime/session state now lives in
 * {@link AgentRuntimeEntry}; remaining BotEntry imports should migrate to the
 * Agent runtime type before this package is removed.
 */
@Deprecated(forRemoval = true)
public final class BotEntry extends AgentRuntimeEntry {
    public BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        super(bot, owner, task);
    }
}
