package server.bots.pq;

import java.awt.*;

/** Mutable state bag for KPQ bot automation. One instance per bot, held in BotEntry. */
public final class BotKpqState {
    // Stage 1
    public int   state               = BotKpqStage1.IDLE;
    public int   couponTarget        = -1;
    public long  waitUntilMs         = 0;
    public int   lastReportedCoupons = 0;
    public Point navTarget           = null;
    // Stage 5
    public boolean stage5Claimed     = false;
}
