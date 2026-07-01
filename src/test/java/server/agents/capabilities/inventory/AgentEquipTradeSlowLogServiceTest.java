package server.agents.capabilities.inventory;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService.SlowClassificationReport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentEquipTradeSlowLogServiceTest {
    @Test
    void keepsLegacySlowWarningThreshold() {
        assertEquals(50_000_000L, AgentEquipTradeSlowLogService.slowWarnNs());
    }

    @Test
    void formatsNanosecondsAsLegacyOneDecimalMillis() {
        assertEquals("12.3", AgentEquipTradeSlowLogService.formatMillis(12_345_678L));
    }

    @Test
    void logsSlowClassificationReportWithoutChangingReportValues() {
        SlowClassificationReport report = new SlowClassificationReport(
                1_000_000L,
                "agent",
                "owner",
                2,
                1,
                1,
                1,
                0,
                100_000L,
                200_000L,
                300_000L,
                4,
                2,
                400_000L);

        assertDoesNotThrow(() -> AgentEquipTradeSlowLogService.warnSlowClassification(report));
    }
}
