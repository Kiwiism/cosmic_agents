package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;
import server.agents.field.AgentBalrogTestFixtureService;
import server.expeditions.ExpeditionType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void easyRunSelectsTwelveLevel60SecondJobPaths() {
        assertEquals(12, AgentBalrogTestFixtureService.ROSTER_SIZE);
        var scenario = new AgentEasyBalrogScenario(1234L);
        assertEquals(12, scenario.roster().size());
        assertEquals(12, scenario.roster().stream().map(
                AgentBalrogTestFixtureService.Build::job).distinct().count());
        assertEquals(2, scenario.spec().partyCount());
        assertEquals(60, AgentBalrogDefinition.LEVEL);
    }

    @Test
    void combatTargetsExcludeScriptKilledReleaseSealAndDisabledCorpse() {
        assertEquals(java.util.Set.of(8830007, 8830008, 8830009),
                AgentBalrogDefinition.COMBAT_MOBS);
        assertEquals(java.util.Set.of(8830008, 8830009), AgentBalrogDefinition.CLAW_MOBS);
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
        var spec = new AgentEasyBalrogScenario(1234L).spec();

        assertEquals(ExpeditionType.BALROG_EASY, spec.expeditionType());
        assertEquals(5_000L, spec.readyCountdownMs());
        assertEquals(AgentBalrogDefinition.RECRUIT_MAP, spec.returnMapId());
        assertEquals(List.of(1, 1), spec.createSelections());
        assertEquals(List.of(1), spec.joinSelections());
        assertEquals(List.of(1, 2, 0), spec.startSelections());
    }

    @Test
    void normalEventExitReturnsClearedMembersToTheEntrance() throws Exception {
        String script = Files.readString(Path.of("scripts/event/BalrogBattle_Easy.js"));

        assertTrue(script.contains("var exitMap = 105100100;"));
        assertTrue(script.contains("player.changeMap(exitMap, 0);"));
    }
}
