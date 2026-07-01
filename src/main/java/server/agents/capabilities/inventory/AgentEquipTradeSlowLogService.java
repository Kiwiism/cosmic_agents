package server.agents.capabilities.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService.SlowClassificationReport;

public final class AgentEquipTradeSlowLogService {
    private static final Logger log = LoggerFactory.getLogger(AgentEquipTradeSlowLogService.class);
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;

    private AgentEquipTradeSlowLogService() {
    }

    public static long slowWarnNs() {
        return TRADE_COMMAND_PROFILE_WARN_NS;
    }

    public static void warnSlowClassification(SlowClassificationReport report) {
        log.warn(
                "Slow equip trade classification: took {} ms bot={} owner={} bagItems={} selfKeep={} normalItems={} reservedOtherItems={} reservedSelfItems={} bagScanMs={} selfKeepMs={} reservedOtherMs={} reservedOtherChecks={} reservedOtherHits={} sortMs={}",
                formatMillis(report.elapsedNs()),
                report.agentName(),
                report.ownerName(),
                report.bagItems(),
                report.selfKeep(),
                report.normalItems(),
                report.reservedOtherItems(),
                report.reservedSelfItems(),
                formatMillis(report.bagScanNs()),
                formatMillis(report.selfKeepNs()),
                formatMillis(report.reservedOtherNs()),
                report.reservedOtherChecks(),
                report.reservedOtherHits(),
                formatMillis(report.sortNs()));
    }

    static String formatMillis(long elapsedNs) {
        return String.format("%.1f", elapsedNs / 1_000_000.0);
    }
}
