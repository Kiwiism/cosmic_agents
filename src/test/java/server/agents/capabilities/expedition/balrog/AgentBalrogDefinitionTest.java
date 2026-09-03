package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;
import server.agents.field.AgentBalrogTestFixtureService;
import server.expeditions.ExpeditionType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBalrogDefinitionTest {

    @Test
    void easyBalrogRecoveryCoversEnvironmentalDrainBeforeAutopotWouldTrigger() {
        assertFalse(AgentEasyBalrogScenario.needsExpeditionRecovery(751, 1_000));
        assertTrue(AgentEasyBalrogScenario.needsExpeditionRecovery(750, 1_000));
        assertTrue(AgentEasyBalrogScenario.needsExpeditionRecovery(0, 1_000));
        assertFalse(AgentEasyBalrogScenario.needsExpeditionRecovery(0, 0));
    }

    @Test
    void postClearFormationKeepsTheNpcTransitionerInInteractionRange() {
        assertEquals(-99, AgentEasyBalrogScenario.expeditionFormationOffset(0, 12, 18));
        assertEquals(99, AgentEasyBalrogScenario.expeditionFormationOffset(11, 12, 18));
    }

    @Test
    void rewardFidgetPeriodsArePerMemberRatherThanLockstep() {
        long first = AgentEasyBalrogScenario.rewardFidgetPeriodMs(0, 1);
        long second = AgentEasyBalrogScenario.rewardFidgetPeriodMs(1, 1);

        assertTrue(first >= 1_400L && first < 3_000L);
        assertTrue(second >= 1_400L && second < 3_000L);
        assertNotEquals(first, second);
    }

    @Test
    void easyRunSelectsTwelveLevel60SecondJobPaths() {
        assertEquals(12, AgentBalrogTestFixtureService.ROSTER_SIZE);
        assertEquals(12, AgentBalrogDefinition.ROSTER_SIZE);
        assertEquals(6, AgentBalrogDefinition.PARTY_CAPACITY);
        var scenario = new AgentEasyBalrogScenario(1234L);
        assertEquals(12, scenario.roster().size());
        assertEquals(12, scenario.roster().stream().map(
                AgentBalrogTestFixtureService.Build::job).distinct().count());
        assertEquals(2, scenario.spec().partyCount());
        assertEquals(60, AgentBalrogDefinition.LEVEL);
    }

    @Test
    void combatTargetsExcludeScriptKilledReleaseSealAndDisabledCorpse() {
        assertEquals(java.util.Set.of(8830007, 8830008, 8830009, 6400008, 6400009),
                AgentBalrogDefinition.COMBAT_MOBS);
        assertEquals(java.util.Set.of(8830008, 8830009), AgentBalrogDefinition.CLAW_MOBS);
        assertEquals(java.util.Set.of(6400008, 6400009), AgentBalrogDefinition.SUMMONED_ADDS);
        assertEquals(8830007, AgentBalrogDefinition.BODY_MOB);
    }

    @Test
    void definitionMatchesEasyBalrogScriptMapsAndNpc() {
        assertEquals(105100100, AgentBalrogDefinition.RECRUIT_MAP);
        assertEquals(105100400, AgentBalrogDefinition.BATTLE_MAP);
        assertEquals(105100401, AgentBalrogDefinition.CLEAR_MAP);
        assertEquals(1061014, AgentBalrogDefinition.ENTRY_NPC);
    }

    @Test
    void scenarioUsesTheEasyNpcRegistrationAndStartSequence() {
        var scenario = new AgentEasyBalrogScenario(1234L);
        var spec = scenario.spec();

        assertEquals(ExpeditionType.BALROG_EASY, spec.expeditionType());
        assertEquals(5_000L, spec.readyCountdownMs());
        assertEquals(AgentBalrogDefinition.RECRUIT_MAP, spec.returnMapId());
        assertEquals(List.of(1, 1), spec.createSelections());
        assertEquals(List.of(1), spec.joinSelections());
        assertEquals(List.of(1, 2, 0), spec.startSelections());
        assertEquals(1, scenario.quickEntryPortalId());
        assertEquals(9, scenario.quickEntrySpacingPx());
        assertEquals(48, scenario.lobbyRallySpacingPx());
        assertTrue(scenario.preserveNonAgentParticipantsAfterClear());
        assertEquals(180_000L, scenario.postClearTimeoutMs());
        assertTrue(scenario.retainReturnedMembersUntilNextRun());
    }

    @Test
    void normalEventExitReturnsClearedMembersToTheEntrance() throws Exception {
        String script = Files.readString(Path.of("scripts/event/BalrogBattle_Easy.js"));

        assertTrue(script.contains("var exitMap = 105100100;"));
        assertTrue(script.contains("player.changeMap(exitMap, 0);"));
        assertTrue(script.contains("EasyBalrogEncounterService"));
        assertTrue(script.contains("Encounter.start"));
        assertTrue(script.contains("if (eim.isEventCleared())"));
        assertFalse(script.contains("eim.schedule(\"releaseLeftClaw\""));
        assertFalse(script.contains("spawnSealedBalrog"));
    }

    @Test
    void clearRoomUsesNativeDropReactorAndRewardExitPortal() throws Exception {
        String reactor = Files.readString(Path.of("scripts/reactor/1052002.js"));
        String portal = Files.readString(Path.of("scripts/portal/balog_end.js"));
        String npc = Files.readString(Path.of("scripts/npc/1061018.js"));

        assertTrue(reactor.contains("sprayItems"));
        assertTrue(reactor.contains("claimEventReactorAction(\"balrog-reward\")"));
        assertTrue(reactor.contains("balrogRewardOpenedAt"));
        assertTrue(reactor.contains("destroyReactor"));
        assertTrue(reactor.indexOf("sprayItems")
                < reactor.indexOf("balrogRewardOpenedAt"));
        assertTrue(reactor.indexOf("balrogRewardOpenedAt")
                < reactor.indexOf("destroyReactor"));
        assertTrue(npc.contains("warpEventTeamToMapSpawnPoint(105100400, 105100401, 0)"));
        assertTrue(portal.contains("pi.gainItem(4001261, 1)"));
        assertTrue(portal.contains("pi.warp(105100100, 0)"));
    }
}
