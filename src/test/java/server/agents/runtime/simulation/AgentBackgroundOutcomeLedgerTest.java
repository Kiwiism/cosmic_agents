package server.agents.runtime.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBackgroundOutcomeLedgerTest {
    @Test
    void mutationFreeWindowReconcilesIdempotently() {
        AgentBackgroundOutcomeLedger ledger = new AgentBackgroundOutcomeLedger();

        ledger.begin(AgentAbstractExecutionScope.TOWN_LIFE, 100L);
        ledger.heartbeat(200L);

        assertTrue(ledger.reconcile());
        assertTrue(ledger.reconcile());
        assertFalse(ledger.snapshot().active());
        assertEquals(1L, ledger.snapshot().heartbeatCount());
        assertEquals(1L, ledger.snapshot().reconciliationCount());
    }

    @Test
    void unsupportedMutationPreventsReconciliation() {
        AgentBackgroundOutcomeLedger ledger = new AgentBackgroundOutcomeLedger();
        ledger.begin(AgentAbstractExecutionScope.TOWN_LIFE, 100L);
        ledger.recordUnsupportedOutcome("quest mutation");

        assertFalse(ledger.reconcile());
        assertTrue(ledger.snapshot().active());
        assertEquals("quest mutation", ledger.snapshot().unsupportedOutcome());
    }
}
