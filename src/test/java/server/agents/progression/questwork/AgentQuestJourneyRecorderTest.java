package server.agents.progression.questwork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.journey.AgentFileJourneyJournalStore;
import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventType;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentQuestJourneyRecorderTest {
    @TempDir
    Path directory;

    @Test
    void recordsDurableQuestCursorAndRecommendedAction() {
        AgentFileJourneyJournalStore store = new AgentFileJourneyJournalStore(directory);
        AgentQuestWorkUnit unit = new AgentQuestWorkUnit(
                1, "work-1", "agent-1", 101, 2018, "catalog-1",
                AgentQuestWorkPhase.ACTIVE, AgentQuestWorkStage.COMPLETE_OBJECTIVES,
                100L, 200L, 106010100, 1, "", "OBJECTIVES_REMAIN", Map.of());
        AgentQuestWorkReconciliation reconciliation = new AgentQuestWorkReconciliation(
                unit, AgentQuestWorkAction.COMPLETE_OBJECTIVES, 0,
                "authoritative objective debt remains");

        AgentJourneyEvent event = new AgentQuestJourneyRecorder(store)
                .recordReconciliation(reconciliation);

        assertEquals(AgentJourneyEventType.QUEST_WORK_RECONCILED, event.type());
        assertEquals("COMPLETE_OBJECTIVES", event.evidence().get("nextAction"));
        assertEquals("1", event.evidence().get("retryCount"));
        assertEquals(event, new AgentFileJourneyJournalStore(directory)
                .read("agent-1").getFirst());
    }
}
