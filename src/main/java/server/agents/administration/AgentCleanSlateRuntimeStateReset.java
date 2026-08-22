package server.agents.administration;

import server.agents.behavior.AgentBehaviorAdaptationFileStore;
import server.agents.objectives.AgentObjectiveCheckpointRuntime;
import server.agents.plans.AgentPlanCheckpointRuntime;
import server.agents.progression.AgentCareerProgressionCheckpointRuntime;
import server.agents.runtime.activity.control.proposal.AgentFileDirectorProposalStore;
import server.agents.runtime.activity.outcome.AgentFileActivityOutcomeInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;

public final class AgentCleanSlateRuntimeStateReset {
    private AgentCleanSlateRuntimeStateReset() {
    }

    public static void clear(int characterId) throws Exception {
        AgentObjectiveCheckpointRuntime.delete(characterId);
        AgentPlanCheckpointRuntime.delete(characterId);
        AgentCareerProgressionCheckpointRuntime.delete(characterId);
        AgentBehaviorAdaptationFileStore.runtimeDefault().delete(characterId);
        AgentFileWorldDirectorSessionStore.runtimeDefault().delete(characterId);
        AgentFileDirectorProposalStore.runtimeDefault().deleteAgent(characterId);
        AgentFileWorldDirectiveInbox.runtimeDefault().deleteAgent(characterId);
        AgentFileActivityOutcomeInbox.runtimeDefault().deleteAgent(characterId);
    }
}
