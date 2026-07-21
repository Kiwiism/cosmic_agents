package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.capabilities.dialogue.semantic.AgentDialogueModel;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;

/** Maple Island content adapter for the generic one-speaker announcement model. */
final class AmherstObjectiveIntentionDialogueModel
        implements AgentDialogueModel<AmherstObjectiveIntentionDialogueModel.Context> {
    record Context(AgentRuntimeEntry entry,
                   Character agent,
                   AmherstPlanObjective objective,
                   long nowMs) {
    }

    @Override
    public String modelId() {
        return "maple-island.objective-intention.v1";
    }

    @Override
    public AgentSemanticDialogueAct produce(Context context) {
        return new AgentSemanticDialogueAct(
                context.agent().getId(), 0, context.nowMs(),
                AgentDialogueTopicRegistry.OBJECTIVE_INTENTION, "announce",
                AgentDialogueAudience.NEARBY_REAL_PLAYER,
                "objective-intention:" + context.objective().objectiveId(),
                2_000L,
                context.entry().sessionGeneration() * 31L + context.objective().objectiveId().hashCode(),
                Map.of("message", AmherstPlanNarrator.message(context.objective()),
                        "objectiveId", context.objective().objectiveId(),
                        "mapId", String.valueOf(context.objective().mapId())));
    }
}
