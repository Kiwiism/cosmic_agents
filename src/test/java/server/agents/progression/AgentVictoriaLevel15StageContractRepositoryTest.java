package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15StageContractRepositoryTest {
    @Test
    void loadsExecutableOrderedPlanWithEveryStageCapabilityDeclared() {
        AgentVictoriaLevel15StageContract plan =
                AgentVictoriaLevel15StageContractRepository.defaultContract();

        assertEquals("victoria-level15-stage-contract-v2", plan.contractId());
        assertEquals(AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().catalogId(),
                plan.catalogId());
        assertEquals(List.of(
                        "complete-biggs-at-olaf", "complete-olaf-lesson", "start-career-path",
                        "pre-job-level-grind", "take-taxi", "enter-instructor", "complete-career-path",
                        "advance-first-job", "allocate-initial-ap-sp",
                        "initial-shop-trip", "return-to-instructor", "instructor-training",
                        "home-quest-pack", "post-home-decision", "rotation-quest-pack",
                        "fallback-grind", "final-return"),
                plan.stages().stream().map(AgentVictoriaLevel15StageContract.Stage::stageId).toList());
        assertTrue(plan.requiredCapabilityIds().containsAll(List.of(
                "progression.career-assignment", "progression.ap-build", "progression.sp-build",
                "equipment.auto-equip",
                "navigation.portal-route", "quest.victoria-handoff",
                "interaction.npc-script", "interaction.npc-shop",
                "quest.instructor-training", "quest.victoria-pre15-packs",
                "combat.generic-grind", "loot.combat-drops",
                "survival.potion-use", "supplies.procurement", "inventory.capacity-management",
                "recovery.physics")));
        assertEquals(104000000, plan.entryCriteria().mapId());
        assertEquals(9, plan.entryCriteria().minimumLevel());
        assertEquals(10, plan.entryCriteria().maximumLevel());
        assertEquals(List.of("lv10", "lv9-olaf", "lv9-grind"),
                plan.entryCriteria().startVariantIds());
        assertEquals(15, plan.exitCriteria().minimumLevel());
        assertTrue(plan.exitCriteria().allInstructorTrainingQuestsComplete());
        assertTrue(plan.exitCriteria().groundedNearInstructor());
    }
}
