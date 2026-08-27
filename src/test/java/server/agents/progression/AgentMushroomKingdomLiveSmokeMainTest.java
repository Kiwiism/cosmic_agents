package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomLiveSmokeMainTest {
    @Test
    void namedSnapshotsReproduceTheQ2323FailureAndStableQ2325Frontier() {
        var returnSnapshot = AgentMushroomKingdomLiveSmokeMain
                .diagnosticSnapshot("q2323-return");
        assertEquals(2323, returnSnapshot.startAtQuestId());
        assertEquals(106020401, returnSnapshot.stageMapId());
        assertTrue(returnSnapshot.activateQuest());
        assertEquals(4000501, returnSnapshot.itemId());
        assertEquals(100, returnSnapshot.itemCount());
        assertNull(returnSnapshot.position());

        var fallenSnapshot = AgentMushroomKingdomLiveSmokeMain
                .diagnosticSnapshot("q2323-out-of-bounds");
        assertEquals(new Point(382, 2_214), fallenSnapshot.position());

        var continuation = AgentMushroomKingdomLiveSmokeMain
                .diagnosticSnapshot("q2325-entry");
        assertEquals(2325, continuation.startAtQuestId());
        assertEquals(AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                continuation.stageMapId());
        assertFalse(continuation.activateQuest());
    }

    @Test
    void blankSnapshotKeepsTheOrdinaryFullRunAndUnknownNamesFailFast() {
        assertNull(AgentMushroomKingdomLiveSmokeMain.diagnosticSnapshot(""));
        assertThrows(IllegalArgumentException.class,
                () -> AgentMushroomKingdomLiveSmokeMain.diagnosticSnapshot("q9999"));
    }

    @Test
    void tenPercentModeRequiresTheRoundedUpSampleBeforeSupplyingTheRemainder() {
        var fifty = AgentMushroomKingdomCatalog.require(2312);
        var twoHundred = AgentMushroomKingdomCatalog.require(2328);
        var oneOff = AgentMushroomKingdomCatalog.require(2326);

        assertEquals(0, AgentMushroomKingdomLiveSmokeMain.tenPercentTopUp(fifty, 4));
        assertEquals(45, AgentMushroomKingdomLiveSmokeMain.tenPercentTopUp(fifty, 5));
        assertEquals(180, AgentMushroomKingdomLiveSmokeMain.tenPercentTopUp(twoHundred, 20));
        assertEquals(0, AgentMushroomKingdomLiveSmokeMain.tenPercentTopUp(oneOff, 0));
        assertEquals(0, AgentMushroomKingdomLiveSmokeMain.tenPercentTopUp(oneOff, 1));
    }

    @Test
    void bossRouteStagingWaitsForTheRoyalSealQuestToStartAtTheEntrance() {
        assertFalse(AgentMushroomKingdomLiveSmokeMain.bossRouteStagingReady(
                client.QuestStatus.Status.NOT_STARTED.getId()));
        assertTrue(AgentMushroomKingdomLiveSmokeMain.bossRouteStagingReady(
                client.QuestStatus.Status.STARTED.getId()));
        assertTrue(AgentMushroomKingdomLiveSmokeMain.bossRouteStagingReady(
                client.QuestStatus.Status.COMPLETED.getId()));
    }

    @Test
    void recoveryCoverageRequiresBothRecoveryQuestsAndRestoredItems() {
        assertTrue(AgentMushroomKingdomLiveSmokeMain.recoveryCoverageComplete(
                true, true, true, true));
        assertFalse(AgentMushroomKingdomLiveSmokeMain.recoveryCoverageComplete(
                false, true, true, true));
        assertFalse(AgentMushroomKingdomLiveSmokeMain.recoveryCoverageComplete(
                true, false, true, true));
        assertFalse(AgentMushroomKingdomLiveSmokeMain.recoveryCoverageComplete(
                true, true, true, false));
    }
}
