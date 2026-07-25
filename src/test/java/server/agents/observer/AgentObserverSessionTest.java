package server.agents.observer;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentObserverSessionTest {
    @Test
    void stationActivationImmediatelyTargetsTheNextApproachMap() {
        AgentObserverSession session = new AgentObserverSession(
                1, 2, 0, 1, mock(AgentRuntimeEntry.class));

        session.beginApproachRoute();

        assertEquals(AgentObserverSession.Stage.APPROACH_ROAM, session.stage);
        assertEquals(1, session.approachIndex);
        assertEquals(30_000, session.destinationMapId);
        assertEquals(0, session.nextDecisionAtMs);
    }

    @Test
    void observationVisitsAdvanceIndependentlyForEachMap() {
        AgentObserverSession session = new AgentObserverSession(
                1, 2, 0, 1, mock(AgentRuntimeEntry.class));

        assertEquals(0, session.nextObservationVisit(20_000));
        assertEquals(1, session.nextObservationVisit(20_000));
        assertEquals(0, session.nextObservationVisit(50_000));
        assertEquals(2, session.nextObservationVisit(20_000));
    }
}
