package server.agents.field;

import client.Character;
import client.Job;
import client.SkinColor;
import constants.skills.ILWizard;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentLpqTestFixtureServiceTest {
    private static final Set<Integer> EXCLUDED_FAMILIES = Set.of(101, 102, 114);

    @Test
    void everyBuildHasACompleteLegalLevelFortyFiveLoadoutWithoutExcludedSlots() {
        for (var entry : AgentLpqTestFixtureService.LPQ_LOADOUTS.entrySet()) {
            for (int gender = 0; gender <= 1; gender++) {
                AgentLpqTestFixtureService.Loadout loadout = entry.getValue();
                List<Integer> equipment = loadout.equipment(gender);
                Set<Integer> slots = new HashSet<>();
                for (int itemId : equipment) {
                    assertFalse(EXCLUDED_FAMILIES.contains(itemId / 10_000),
                            () -> entry.getKey() + " equipped an excluded item " + itemId);
                    int slot = slotFamily(itemId);
                    assertTrue(slot > 0, () -> entry.getKey() + " has unsupported item " + itemId);
                    assertTrue(slots.add(slot), () -> entry.getKey() + " duplicated slot " + slot);
                }
                assertTrue(slots.containsAll(Set.of(100, 103, 107, 108, 110, 130)));
                assertEquals(loadout.weaponItemId(), equipment.getLast());
                assertTrue(equipment.contains(1_032_075));
                assertTrue(equipment.contains(1_102_055));
            }
        }
    }

    @Test
    void loadoutsUseTheRequestedWeaponAndPrimaryCapeScrollFamilies() {
        assertScrolls("cleric-wand", 2_043_701, 2_041_017);
        assertScrolls("il-wizard-wand", 2_043_701, 2_041_017);
        assertScrolls("bandit-dagger", 2_043_301, 2_041_023);
        assertScrolls("assassin-claw", 2_044_701, 2_041_023);
        assertScrolls("crossbowman-crossbow", 2_044_601, 2_041_020);
        assertScrolls("spearman-spear", 2_044_301, 2_041_014);
        assertEquals(1_432_020,
                AgentLpqTestFixtureService.LPQ_LOADOUTS.get("spearman-spear").weaponItemId());
    }

    @Test
    void rosterUsesTheRequestedSixJobsInOrderAndGivesIceLightningAoe() {
        assertEquals(List.of(
                "cleric-wand", "il-wizard-wand", "bandit-dagger", "assassin-claw",
                "crossbowman-crossbow", "spearman-spear"), AgentLpqTestFixtureService.BUILD_IDS);
        assertEquals(List.of(
                        Job.CLERIC, Job.IL_WIZARD, Job.BANDIT, Job.ASSASSIN,
                        Job.CROSSBOWMAN, Job.SPEARMAN),
                AgentLpqTestFixtureService.BUILD_IDS.stream()
                        .map(AgentLpqTestFixtureService::build)
                        .map(AgentBalrogTestFixtureService.Build::job)
                        .toList());
        assertTrue(AgentLpqTestFixtureService.build("il-wizard-wand").spBuild().stream()
                .anyMatch(step -> step.skillId() == ILWizard.THUNDERBOLT
                        && step.targetLevel() == 30));
    }

    @Test
    void appearanceCatalogUsesAllStylesGendersAndRequestedSkinTonesDeterministically() {
        assertTrue(AgentLpqAppearanceCatalog.faces(0).size() > 200);
        assertTrue(AgentLpqAppearanceCatalog.faces(1).size() > 200);
        assertTrue(AgentLpqAppearanceCatalog.hair(0).size() > 700);
        assertTrue(AgentLpqAppearanceCatalog.hair(1).size() > 700);
        assertTrue(AgentLpqAppearanceCatalog.hair(0).stream().anyMatch(id -> id / 1_000 == 33));
        assertTrue(AgentLpqAppearanceCatalog.hair(1).stream().anyMatch(id -> id / 1_000 == 34));

        Set<Integer> genders = new HashSet<>();
        Set<SkinColor> skins = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            AgentLpqAppearanceCatalog.Appearance first =
                    AgentLpqAppearanceCatalog.select(seed);
            assertEquals(first, AgentLpqAppearanceCatalog.select(seed));
            assertTrue(first.gender() == 0 || first.gender() == 1);
            assertTrue(AgentLpqAppearanceCatalog.faces(first.gender()).contains(first.faceId()));
            assertTrue(AgentLpqAppearanceCatalog.hair(first.gender()).contains(first.hairId()));
            assertTrue(AgentLpqAppearanceCatalog.SKIN_COLORS.contains(first.skinColor()));
            genders.add(first.gender());
            skins.add(first.skinColor());
        }
        assertEquals(Set.of(0, 1), genders);
        assertEquals(Set.of(
                SkinColor.LIGHT, SkinColor.TANNED, SkinColor.DARK, SkinColor.PALE), skins);
    }

    @Test
    void appliesTheSelectedGenderSkinHairAndFaceTogether() {
        Character agent = mock(Character.class);
        AgentLpqAppearanceCatalog.Appearance appearance =
                AgentLpqTestFixtureService.applyAppearance(agent, 91_337L);

        verify(agent).setGender(appearance.gender());
        verify(agent).setSkinColor(appearance.skinColor());
        verify(agent).setHair(appearance.hairId());
        verify(agent).setFace(appearance.faceId());
    }

    private static void assertScrolls(String buildId, int weaponScroll, int capeScroll) {
        AgentLpqTestFixtureService.Loadout loadout =
                AgentLpqTestFixtureService.LPQ_LOADOUTS.get(buildId);
        assertNotNull(loadout);
        assertEquals(weaponScroll, loadout.weaponScrollItemId());
        assertEquals(capeScroll, loadout.capeScrollItemId());
    }

    private static int slotFamily(int itemId) {
        int family = itemId / 10_000;
        return family >= 130 && family <= 149 ? 130 : family;
    }
}
