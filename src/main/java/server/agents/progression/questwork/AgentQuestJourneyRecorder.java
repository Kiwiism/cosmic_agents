package server.agents.progression.questwork;

import server.agents.progression.questwork.shadow.AgentQuestWorkShadowReport;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.Map;

/** Records quest reconciliation and shadow parity without changing quest execution. */
public final class AgentQuestJourneyRecorder {
    private final AgentJourneyJournalStore journal;

    public AgentQuestJourneyRecorder(AgentJourneyJournalStore journal) {
        if (journal == null) throw new IllegalArgumentException("journey journal is required");
        this.journal = journal;
    }

    public AgentJourneyEvent recordReconciliation(AgentQuestWorkReconciliation result) {
        if (result == null) throw new IllegalArgumentException("quest reconciliation is required");
        AgentQuestWorkUnit unit = result.workUnit();
        return journal.append(new AgentJourneyEventDraft(
                "quest-work:" + unit.workUnitId() + ':' + unit.updatedAtMs(),
                unit.agentId(), unit.characterId(), unit.updatedAtMs(),
                AgentJourneyEventType.QUEST_WORK_RECONCILED, AgentActivityKind.QUESTING,
                "quest-work", unit.workUnitId(), result.reason(),
                Map.of("questId", Integer.toString(unit.questId()),
                        "phase", unit.phase().name(),
                        "stage", unit.stage().name(),
                        "nextAction", result.nextAction().name(),
                        "destinationMapId", Integer.toString(result.destinationMapId()),
                        "retryCount", Integer.toString(unit.retryCount()))));
    }

    public AgentJourneyEvent recordShadow(
            String agentId,
            int characterId,
            AgentQuestWorkShadowReport report) {
        if (report == null) throw new IllegalArgumentException("quest shadow report is required");
        return journal.append(new AgentJourneyEventDraft(
                "quest-shadow:" + report.durableRecommendation().workUnit().workUnitId()
                        + ':' + report.existingPlan().capturedAtMs(),
                agentId, characterId, report.existingPlan().capturedAtMs(),
                AgentJourneyEventType.QUEST_SHADOW_COMPARED, AgentActivityKind.QUESTING,
                "quest-shadow", report.durableRecommendation().workUnit().workUnitId(),
                report.explanation(),
                Map.of("comparison", report.comparison().name(),
                        "planId", report.existingPlan().planId(),
                        "stepId", report.existingPlan().stepId(),
                        "existingAction", report.existingPlan().action().name(),
                        "durableAction", report.durableRecommendation().nextAction().name())));
    }
}
