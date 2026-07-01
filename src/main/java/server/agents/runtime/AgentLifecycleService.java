package server.agents.runtime;

import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent-owned lifecycle map mutation helpers over the temporary BotEntry store.
 * BotManager still supplies runtime side effects such as scheduled-task canceling.
 */
public final class AgentLifecycleService {
    private AgentLifecycleService() {
    }

    public static void removeLeaderEntries(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                           Map<Integer, ?> leaderFormations,
                                           Map<Integer, ?> townClusterAnchors,
                                           int leaderCharId,
                                           Consumer<BotEntry> beforeRemove) {
        List<BotEntry> entries = entriesByLeaderId.remove(leaderCharId);
        if (entries != null) {
            for (BotEntry entry : entries) {
                beforeRemove.accept(entry);
            }
        }
        leaderFormations.remove(leaderCharId);
        townClusterAnchors.remove(leaderCharId);
    }

    public static boolean removeAgentByCharacterId(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                                   Map<Integer, ?> leaderFormations,
                                                   Map<Integer, ?> townClusterAnchors,
                                                   int agentCharId,
                                                   Consumer<BotEntry> beforeRemove) {
        boolean removed = false;
        for (Map.Entry<Integer, List<BotEntry>> leaderEntry : entriesByLeaderId.entrySet()) {
            List<BotEntry> entries = leaderEntry.getValue();
            boolean removedFromLeader = entries.removeIf(entry -> {
                if (AgentBotRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                    beforeRemove.accept(entry);
                    return true;
                }
                return false;
            });
            if (removedFromLeader) {
                removed = true;
                if (entries.isEmpty() && entriesByLeaderId.remove(leaderEntry.getKey(), entries)) {
                    leaderFormations.remove(leaderEntry.getKey());
                    townClusterAnchors.remove(leaderEntry.getKey());
                }
            }
        }
        return removed;
    }
}
