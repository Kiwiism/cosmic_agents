package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;
import server.agents.field.AgentBalrogTestFixtureService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEasyBalrogRewardPolicyTest {
    @Test
    void weaponRewardsPreferTheMatchingBuildClass() {
        var members = List.of(
                member(10, 0, AgentBalrogTestFixtureService.WeaponClass.TWO_HANDED_SWORD),
                member(20, 1, AgentBalrogTestFixtureService.WeaponClass.STAFF));

        var assignments = AgentEasyBalrogRewardPolicy.assign(members, List.of(
                new AgentEasyBalrogRewardPolicy.Drop(101, 1_402_056),
                new AgentEasyBalrogRewardPolicy.Drop(102, 1_382_066)));

        assertEquals(10, assignments.get(101));
        assertEquals(20, assignments.get(102));
    }

    @Test
    void genericRewardsAreDistributedBeforeAnyoneGetsASecondShare() {
        var members = List.of(
                member(10, 0, AgentBalrogTestFixtureService.WeaponClass.BOW),
                member(20, 1, AgentBalrogTestFixtureService.WeaponClass.CROSSBOW),
                member(30, 2, AgentBalrogTestFixtureService.WeaponClass.GUN));
        var drops = List.of(
                new AgentEasyBalrogRewardPolicy.Drop(101, 2_040_728),
                new AgentEasyBalrogRewardPolicy.Drop(102, 2_040_729),
                new AgentEasyBalrogRewardPolicy.Drop(103, 2_040_730),
                new AgentEasyBalrogRewardPolicy.Drop(104, 2_040_731));

        var assignments = AgentEasyBalrogRewardPolicy.assign(members, drops);

        assertEquals(3, assignments.values().stream().limit(3).distinct().count());
        assertTrue(assignments.values().stream().filter(id -> id == 10).count() <= 2);
    }

    @Test
    void duplicateClassRewardsDoNotLetOneMatchingAgentVacuumTheSpray() {
        var members = List.of(
                member(10, 0, AgentBalrogTestFixtureService.WeaponClass.TWO_HANDED_SWORD),
                member(20, 1, AgentBalrogTestFixtureService.WeaponClass.STAFF),
                member(30, 2, AgentBalrogTestFixtureService.WeaponClass.GUN));

        var assignments = AgentEasyBalrogRewardPolicy.assign(members, List.of(
                new AgentEasyBalrogRewardPolicy.Drop(101, 1_402_056),
                new AgentEasyBalrogRewardPolicy.Drop(102, 1_402_057),
                new AgentEasyBalrogRewardPolicy.Drop(103, 1_402_058)));

        assertEquals(10, assignments.get(101));
        assertEquals(20, assignments.get(102));
        assertEquals(30, assignments.get(103));
    }

    private static AgentEasyBalrogRewardPolicy.Member member(
            int id, int ordinal, AgentBalrogTestFixtureService.WeaponClass weaponClass) {
        return new AgentEasyBalrogRewardPolicy.Member(id, ordinal, weaponClass);
    }
}
