package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTownLifeProgressWatchdogTest {
    @Test
    void reportsNoProgressWithoutMistakingIncrementalMovementForAStall() {
        AgentTownLifeProgressWatchdog watchdog = new AgentTownLifeProgressWatchdog();
        watchdog.begin(new Point(200, 0), 0L);

        assertEquals(AgentTownLifeProgressWatchdog.Result.PROGRESSING,
                watchdog.observe(new Point(0, 0), 1L));
        assertEquals(AgentTownLifeProgressWatchdog.Result.PROGRESSING,
                watchdog.observe(new Point(7, 0), 4_000L));
        assertEquals(AgentTownLifeProgressWatchdog.Result.PROGRESSING,
                watchdog.observe(new Point(14, 0), 7_999L));
        assertEquals(AgentTownLifeProgressWatchdog.Result.STALLED,
                watchdog.observe(new Point(14, 0), 15_999L));
    }

    @Test
    void totalTimeoutBreaksMovingLoops() {
        AgentTownLifeProgressWatchdog watchdog = new AgentTownLifeProgressWatchdog();
        watchdog.begin(new Point(1_000, 0), 0L);

        assertEquals(AgentTownLifeProgressWatchdog.Result.PROGRESSING,
                watchdog.observe(new Point(0, 0), 1L));
        assertEquals(AgentTownLifeProgressWatchdog.Result.TIMED_OUT,
                watchdog.observe(new Point(20, 0), 60_000L));
    }
}
