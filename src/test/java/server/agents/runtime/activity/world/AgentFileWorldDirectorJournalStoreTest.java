package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFileWorldDirectorJournalStoreTest {
    @TempDir
    Path directory;

    @Test
    void appendsAndReadsBoundedRecentShadowEvidence() {
        AgentFileWorldDirectorJournalStore store =
                new AgentFileWorldDirectorJournalStore(directory);
        AgentWorldShadowReport report = AgentWorldShadowEvaluator.baseline().evaluate(
                AgentWorldMilestoneEvaluatorTest.context(
                        18, 100, 100_000_000, "COMPLETE", false));

        store.append(report.journalEntry());
        store.append(report.journalEntry());

        assertEquals(1, store.recent(27, 1).size());
        assertEquals(report.decision().proposalId(),
                store.recent(27, 1).getFirst().selectedProposalId());
    }
}
