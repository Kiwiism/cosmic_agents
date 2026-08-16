package server.agents.economy.integration.cosmic;

import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.PopulationAdmissionPlanner;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyAgentRosterBinderTest {
    @Test
    void bindsScenarioSlotsToMatchingRealJobFamiliesNotCharacterOrder() {
        Character magician = character(10, Job.MAGICIAN);
        Character warrior = character(20, Job.WARRIOR);
        var admissions = List.of(admission("agent-1", "warrior"),
                admission("agent-2", "magician"));

        var bound = new EconomyAgentRosterBinder().bind(admissions, List.of(magician, warrior));

        assertEquals(warrior, bound.get("agent-1"));
        assertEquals(magician, bound.get("agent-2"));
    }

    @Test
    void failsClosedWhenTheLiveRosterCannotMeetConfiguredDemand() {
        var admissions = List.of(admission("agent-1", "warrior"),
                admission("agent-2", "magician"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new EconomyAgentRosterBinder().bind(admissions,
                        List.of(character(10, Job.WARRIOR), character(20, Job.WARRIOR))));

        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("magician"));
    }

    @Test
    void reservesRealPlayerShopPermitsForConfiguredSellerProfiles() {
        Character buyer = character(10, Job.WARRIOR, false);
        Character permittedSeller = character(20, Job.WARRIOR, true);
        var admissions = List.of(admission("agent-1", "warrior", .8),
                admission("agent-2", "warrior", .1));

        var bound = new EconomyAgentRosterBinder().bind(admissions,
                List.of(buyer, permittedSeller), 5140000);

        assertEquals(permittedSeller, bound.get("agent-1"));
        assertEquals(buyer, bound.get("agent-2"));
    }

    @Test
    void failsClosedWhenAConfiguredSellerHasNoRealPermit() {
        var admissions = List.of(admission("agent-1", "warrior", .8));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new EconomyAgentRosterBinder().bind(admissions,
                        List.of(character(10, Job.WARRIOR, false)), 5140000));

        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("missingSellerPermits"));
    }

    private static PopulationAdmissionPlanner.Admission admission(String id, String job) {
        return admission(id, job, .5);
    }

    private static PopulationAdmissionPlanner.Admission admission(String id, String job, double stallWillingness) {
        return new PopulationAdmissionPlanner.Admission(new EconomyAgentProfile(id, job,
                .5, .5, .5, .5, .5, stallWillingness, 24, .5, .5), Instant.EPOCH);
    }

    private static Character character(int id, Job job) {
        return character(id, job, false);
    }

    private static Character character(int id, Job job, boolean permit) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getJob()).thenReturn(job);
        Inventory cash = mock(Inventory.class);
        when(cash.countById(5140000)).thenReturn(permit ? 1 : 0);
        when(character.getInventory(InventoryType.CASH)).thenReturn(cash);
        return character;
    }
}
