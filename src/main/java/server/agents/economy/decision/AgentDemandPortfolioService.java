package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds demand from one agent's actual state; it never accepts aggregate item-demand multipliers. */
public final class AgentDemandPortfolioService {
    public List<AgentNeed> build(AgentEconomicState state, Instant logicalAt) {
        List<AgentNeed> needs = new ArrayList<>();
        for (ResourceRequirement resource : state.resources()) {
            needs.add(need(resource.itemId(), resource.ownedQuantity(), resource.targetQuantity(),
                    resource.urgency(), resource.ammunition()
                            ? EconomicReason.AMMUNITION_RESTOCK : EconomicReason.CONSUMABLE_RESTOCK,
                    logicalAt, resource.maximumWillingnessToPay(), resource.substitutes(), Set.of(),
                    "actual runway deficit="
                            + Math.max(0, resource.targetQuantity() - resource.ownedQuantity())));
        }
        for (QuestObjective objective : state.questObjectives()) {
            if (!objective.accepted() || objective.completed()) continue;
            int ownedCredit = objective.tradeAcquisitionCounts() ? objective.ownedQuantity() : 0;
            int current = Math.max(objective.progress(), ownedCredit);
            needs.add(need(objective.itemId(), current, objective.requiredQuantity(), objective.urgency(),
                    EconomicReason.QUEST_REQUIREMENT, logicalAt, objective.maximumWillingnessToPay(),
                    Set.of(), Set.of(), "accepted quest=" + objective.questId()
                            + " remaining=" + Math.max(0, objective.requiredQuantity() - current)));
        }
        for (EquipmentUpgrade upgrade : state.equipmentUpgrades()) {
            if (upgrade.requiredLevel() <= state.level() && upgrade.marginalUtility() > 0
                    && !upgrade.alreadyOwned()) {
                needs.add(need(upgrade.itemId(), 0, 1, upgrade.urgency(), EconomicReason.EQUIPMENT_UPGRADE,
                        logicalAt, upgrade.maximumWillingnessToPay(), upgrade.substitutes(),
                        upgrade.compatibleScrollIds(), "slot=" + upgrade.slot()
                                + " marginalUtility=" + upgrade.marginalUtility()));
            }
        }
        for (ScrollProject project : state.scrollProjects()) {
            if (project.ownedTargetEquipment() && project.remainingSlots() > 0
                    && project.expectedUtility() > 0) {
                needs.add(need(project.scrollItemId(), project.ownedScrolls(), project.targetScrolls(),
                        project.urgency(), EconomicReason.SCROLL_UPGRADE, logicalAt,
                        project.maximumWillingnessToPay(), project.substituteScrollIds(),
                        Set.of(project.targetEquipmentItemId()), "target=" + project.targetEquipmentItemId()
                                + " slots=" + project.remainingSlots() + " expectedUtility="
                                + project.expectedUtility()));
            }
        }
        for (ChairPreference chair : state.chairPreferences()) {
            if (!chair.alreadyOwned() && chair.preferenceUtility() > 0) {
                needs.add(need(chair.itemId(), 0, 1, chair.urgency(), EconomicReason.COLLECTIBLE_OR_CHAIR,
                        logicalAt, chair.maximumWillingnessToPay(), chair.substitutes(), Set.of(),
                        "chair preference utility=" + chair.preferenceUtility()));
            }
        }
        return List.copyOf(needs);
    }

    private static AgentNeed need(int itemId, int current, int target, double urgency,
                                  EconomicReason reason, Instant now, long wtp,
                                  Set<Integer> substitutes, Set<Integer> complements, String evidence) {
        return new AgentNeed(itemId, current, target, urgency, reason, now, wtp,
                substitutes, complements, evidence);
    }

    public record AgentEconomicState(int level, List<ResourceRequirement> resources,
                                     List<QuestObjective> questObjectives,
                                     List<EquipmentUpgrade> equipmentUpgrades,
                                     List<ScrollProject> scrollProjects,
                                     List<ChairPreference> chairPreferences) {
        public AgentEconomicState {
            if (level <= 0) throw new IllegalArgumentException();
            resources = List.copyOf(resources); questObjectives = List.copyOf(questObjectives);
            equipmentUpgrades = List.copyOf(equipmentUpgrades); scrollProjects = List.copyOf(scrollProjects);
            chairPreferences = List.copyOf(chairPreferences);
        }
    }
    public record ResourceRequirement(int itemId, int ownedQuantity, int targetQuantity, double urgency,
                                      long maximumWillingnessToPay, boolean ammunition,
                                      Set<Integer> substitutes) { }
    public record QuestObjective(int questId, int itemId, int requiredQuantity, int progress,
                                 int ownedQuantity, boolean accepted, boolean completed,
                                 boolean tradeAcquisitionCounts, double urgency,
                                 long maximumWillingnessToPay) { }
    public record EquipmentUpgrade(int itemId, String slot, int requiredLevel, boolean alreadyOwned,
                                   double marginalUtility, double urgency, long maximumWillingnessToPay,
                                   Set<Integer> substitutes, Set<Integer> compatibleScrollIds) { }
    public record ScrollProject(int scrollItemId, int targetEquipmentItemId,
                                boolean ownedTargetEquipment, int remainingSlots, int ownedScrolls,
                                int targetScrolls, double expectedUtility, double urgency,
                                long maximumWillingnessToPay, Set<Integer> substituteScrollIds) { }
    public record ChairPreference(int itemId, boolean alreadyOwned, double preferenceUtility,
                                  double urgency, long maximumWillingnessToPay,
                                  Set<Integer> substitutes) { }
}
