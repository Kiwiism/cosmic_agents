package server.agents.capabilities.inventory.demand;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Character adapter for forecasts and authoritative active/committed quest reservations. */
public final class AgentQuestItemDemandRuntime {
    public static final String RESERVATION_CAPABILITY = "quest-item-demand";
    private static final int RESERVATION_PRIORITY = config.AgentTuning.intValue(
            "server.agents.capabilities.inventory.demand.AgentQuestItemDemandRuntime.RESERVATION_PRIORITY");
    private static final AgentQuestItemDemandForecastService FORECASTS =
            AgentQuestItemDemandForecastService.defaultService();

    private AgentQuestItemDemandRuntime() {
    }

    public static AgentQuestItemDemandForecast forecast(
            Character agent,
            Set<Integer> committedQuestIds,
            Set<Integer> plannedJobIds) {
        if (agent == null) {
            return new AgentQuestItemDemandForecast("", "", java.util.List.of());
        }
        AgentQuestItemDemandIndex index =
                AgentQuestItemDemandIndexRepository.defaultRepository().index();
        Map<Integer, Integer> statuses = new HashMap<>();
        Map<Integer, Integer> itemCounts = new HashMap<>();
        for (AgentQuestItemDemandIndex.Entry item : index.entries()) {
            Inventory etc = agent.getInventory(InventoryType.ETC);
            itemCounts.put(item.itemId(), etc == null ? 0 : etc.countById(item.itemId()));
            for (AgentQuestItemDemandIndex.QuestDemand demand : item.quests()) {
                statuses.putIfAbsent(demand.questId(), (int) agent.getQuestStatus(demand.questId()));
                for (AgentQuestItemDemandIndex.Prerequisite prerequisite
                        : demand.prerequisiteRequirements()) {
                    statuses.putIfAbsent(prerequisite.questId(),
                            (int) agent.getQuestStatus(prerequisite.questId()));
                }
            }
        }
        AgentQuestDemandProfile profile = new AgentQuestDemandProfile(
                agent.getLevel(), agent.getJob().getId(),
                plannedJobIds == null ? Set.of() : plannedJobIds,
                statuses,
                committedQuestIds == null ? Set.of() : committedQuestIds,
                itemCounts);
        return FORECASTS.forecast(profile);
    }

    public static AgentQuestItemDemandForecast refreshReservations(
            AgentRuntimeEntry entry,
            Character agent,
            Set<Integer> committedQuestIds,
            Set<Integer> plannedJobIds,
            long nowMs) {
        AgentQuestItemDemandForecast forecast = forecast(agent, committedQuestIds, plannedJobIds);
        Map<Integer, Integer> reservations = new HashMap<>();
        for (AgentQuestItemDemandForecast.ItemForecast item : forecast.items()) {
            int required = item.authoritativeDemand();
            if (required > 0) {
                reservations.put(item.itemId(), required);
            }
        }
        if (reservations.isEmpty()) {
            AgentInventoryReservationRuntime.releaseCapability(entry, RESERVATION_CAPABILITY);
        } else {
            AgentInventoryReservationRuntime.reserveObjectiveItems(
                    entry, reservations, RESERVATION_CAPABILITY,
                    AgentDisposition.QUEST_RESERVE,
                    "active or committed quest item demand",
                    RESERVATION_PRIORITY, nowMs);
        }
        return forecast;
    }

    public static Set<Integer> activeQuestIds(AgentQuestItemDemandForecast forecast) {
        Set<Integer> active = new HashSet<>();
        for (AgentQuestItemDemandForecast.ItemForecast item : forecast.items()) {
            for (AgentQuestItemDemandForecast.QuestEvidence evidence : item.evidence()) {
                if (evidence.category() == AgentQuestDemandCategory.ACTIVE) {
                    active.add(evidence.questId());
                }
            }
        }
        return Set.copyOf(active);
    }
}
