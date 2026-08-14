package server.agents.economy.integration.cosmic;

import client.Character;
import client.QuestStatus;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.decision.AgentDemandPortfolioService;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyEngineConfig;
import server.quest.Quest;

import java.time.Instant;
import java.util.*;

/** Builds needs from live holdings, accepted quests, configured policies, and real NPC prices. */
public final class CosmicAgentNeedReader implements AutonomousFreeMarketBehavior.AgentNeedReader {
    private final EconomyEngineConfig.Demand config;
    private final EconomyCatalog catalog;
    private final AgentDemandPortfolioService portfolios = new AgentDemandPortfolioService();

    public CosmicAgentNeedReader(EconomyEngineConfig.Demand config, EconomyCatalog catalog) {
        this.config = Objects.requireNonNull(config); this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public List<AgentNeed> read(Character agent, EconomyAgentProfile profile, Instant logicalAt) {
        List<AgentDemandPortfolioService.ResourceRequirement> resources = new ArrayList<>();
        for (EconomyEngineConfig.ResourceTarget target : config.resourceTargets) {
            if (!target.jobs.isEmpty() && target.jobs.stream().noneMatch(profile.jobFamily()::equalsIgnoreCase))
                continue;
            int owned = count(agent, target.itemId);
            long unit = npcUnitPrice(target.npcId, target.itemId);
            int deficit = Math.max(1, target.targetQuantity - owned);
            long lots = ItemConstants.isRechargeable(target.itemId)
                    ? Math.max(1, (deficit + (long) information().getSlotMax(agent.getClient(), target.itemId) - 1)
                    / information().getSlotMax(agent.getClient(), target.itemId)) : deficit;
            long wtp = Math.min(agent.getMeso(), Math.multiplyExact(unit, lots));
            resources.add(new AgentDemandPortfolioService.ResourceRequirement(target.itemId, owned,
                    target.targetQuantity, target.urgency, wtp, ItemConstants.isRechargeable(target.itemId), Set.of()));
        }
        List<AgentDemandPortfolioService.QuestObjective> objectives = new ArrayList<>();
        ItemInformationProvider information = ItemInformationProvider.getInstance();
        for (QuestStatus status : agent.getStartedQuests()) {
            for (Map.Entry<Integer, Integer> requirement
                    : Quest.getInstance(status.getQuestID()).getCompleteItemRequirements().entrySet()) {
                int itemId = requirement.getKey();
                int required = requirement.getValue();
                if (required <= 0 || information.isUnmerchable(itemId)) continue;
                int owned = count(agent, itemId);
                long wtp = Math.max(0, Math.round(agent.getMeso() * config.questMaximumWalletFraction));
                objectives.add(new AgentDemandPortfolioService.QuestObjective(status.getQuestID(),
                        itemId, required, 0, owned, true, false,
                        true, .8, wtp));
            }
        }
        var state = new AgentDemandPortfolioService.AgentEconomicState(agent.getLevel(), resources,
                objectives, List.of(), List.of(), List.of());
        return portfolios.build(state, logicalAt);
    }

    public List<ResourceProcurement> missingNpcResources(Character agent, EconomyAgentProfile profile) {
        List<ResourceProcurement> result = new ArrayList<>();
        for (EconomyEngineConfig.ResourceTarget target : config.resourceTargets) {
            if (!target.jobs.isEmpty() && target.jobs.stream().noneMatch(profile.jobFamily()::equalsIgnoreCase))
                continue;
            int missing = target.targetQuantity - count(agent, target.itemId);
            if (missing > 0) result.add(new ResourceProcurement(target.npcId, target.itemId,
                    Math.min(missing, target.purchaseLot)));
        }
        return List.copyOf(result);
    }

    private long npcUnitPrice(int npcId, int itemId) {
        return catalog.npcShop(npcId).flatMap(shop -> shop.items().stream()
                .filter(item -> item.itemId() == itemId).findFirst()).map(item -> (long) item.price()).orElse(0L);
    }
    private static ItemInformationProvider information() {
        return ItemInformationProvider.getInstance();
    }
    private static int count(Character agent, int itemId) {
        return agent.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
    }
    public record ResourceProcurement(int npcId, int itemId, int quantity) { }
}
