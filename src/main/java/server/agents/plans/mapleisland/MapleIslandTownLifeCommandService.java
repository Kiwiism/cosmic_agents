package server.agents.plans.mapleisland;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.LithHarborTownLifeCatalog;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/** Maple Island command adapter that hands eligible Agents to the generic TownLife capability. */
public final class MapleIslandTownLifeCommandService {
    private MapleIslandTownLifeCommandService() {
    }

    public static Result startCompletedSouthperryAgents(Character issuer, long nowMs) {
        int started = 0;
        int alreadyActive = 0;
        int notEligible = 0;
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (!sameChannel(issuer, agent)) {
                continue;
            }
            AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
            if (state.enabled()) {
                alreadyActive++;
                continue;
            }
            if (!eligible(entry, agent)) {
                notEligible++;
                continue;
            }
            AgentTownLifeRuntime.start(entry, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                    nowMs, agent.getId());
            started++;
        }
        return new Result(started, alreadyActive, notEligible);
    }

    public static int stop(Character issuer) {
        int stopped = 0;
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (!sameChannel(issuer, agent) || !AgentTownLifeRuntime.active(entry)) {
                continue;
            }
            AgentTownLifeRuntime.stop(entry, agent);
            stopped++;
        }
        return stopped;
    }

    public static Status status(Character issuer) {
        int traveling = 0;
        int inTown = 0;
        int inShops = 0;
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (!sameChannel(issuer, agent) || !AgentTownLifeRuntime.active(entry)) {
                continue;
            }
            AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
            if (state.stage() == AgentTownLifeState.Stage.TRAVEL_TO_TOWN) {
                traveling++;
            } else if (agent.getMapId() == state.townMapId()) {
                inTown++;
            } else {
                inShops++;
            }
        }
        return new Status(traveling, inTown, inShops);
    }

    static boolean eligible(AgentRuntimeEntry entry, Character agent) {
        return entry != null
                && agent != null
                && entry.amherstPlanExecutionState().completed()
                && agent.getMapId() == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                && agent.getQuestStatus(MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID)
                == QuestStatus.Status.STARTED.getId();
    }

    private static boolean sameChannel(Character issuer, Character agent) {
        return issuer != null
                && agent != null
                && issuer.getWorld() == agent.getWorld()
                && issuer.getClient() != null
                && agent.getClient() != null
                && issuer.getClient().getChannel() == agent.getClient().getChannel();
    }

    public record Result(int started, int alreadyActive, int notEligible) {
    }

    public record Status(int traveling, int inTown, int inShops) {
        public int total() {
            return traveling + inTown + inShops;
        }
    }
}
