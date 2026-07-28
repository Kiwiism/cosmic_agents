package server.agents.capabilities.inventory.demand;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemRestrictionPolicy;
import server.agents.capabilities.inventory.AgentInventoryItemPolicy;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.integration.InventoryGateway;
import server.agents.progression.AgentCareerProgressionState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds proposals and enforces only the explicitly configured safe ETC sale subset. */
public final class AgentQuestItemDispositionRuntime {
    private static final Logger log =
            LoggerFactory.getLogger(AgentQuestItemDispositionRuntime.class);
    private static final AgentItemDispositionProposalService PROPOSALS =
            new AgentItemDispositionProposalService();

    private AgentQuestItemDispositionRuntime() {
    }

    public static List<AgentEtcSaleCandidate> collectShopSaleCandidates(
            AgentRuntimeEntry entry,
            Character agent,
            InventoryGateway inventory,
            long nowMs) {
        AgentQuestItemSellMode mode = AgentQuestItemSellMode.configured();
        if (mode == AgentQuestItemSellMode.DISABLED || entry == null || agent == null) {
            return List.of();
        }
        Set<Integer> plannedJobs = plannedJobs(entry);
        AgentQuestItemDemandForecast forecast =
                AgentQuestItemDemandRuntime.forecast(agent, Set.of(), plannedJobs);
        Map<Integer, AgentQuestItemDemandForecast.ItemForecast> forecasts =
                forecast.items().stream().collect(Collectors.toMap(
                        AgentQuestItemDemandForecast.ItemForecast::itemId,
                        Function.identity()));
        List<Item> safeEtc = AgentInventoryItemPolicy.collectSafeItems(
                agent, InventoryType.ETC, item -> true, inventory::isQuestItem,
                itemId -> ItemRestrictionPolicy.allowsUntradeable(agent, itemId));
        List<AgentEtcSaleCandidate> candidates = new ArrayList<>();
        for (Item item : safeEtc) {
            AgentQuestItemDemandIndex.Entry facts =
                    AgentQuestItemDemandIndexRepository.defaultRepository()
                            .findItem(item.getItemId()).orElse(null);
            if (facts == null) {
                continue;
            }
            int owned = count(agent, item.getItemId());
            AgentQuestItemDemandForecast.ItemForecast itemForecast = forecasts.get(item.getItemId());
            if (itemForecast == null) {
                itemForecast = new AgentQuestItemDemandForecast.ItemForecast(
                        item.getItemId(), facts.itemName(), owned, Map.of(), List.of());
            }
            int reserved = AgentInventoryReservationRuntime.ledger(entry)
                    .reservedQuantity(item.getItemId(), nowMs);
            Map<String, Integer> cohortShortages =
                    cohortShortages(entry, item.getItemId(), nowMs);
            AgentItemDispositionProposal proposal = PROPOSALS.propose(
                    itemForecast, reserved, cohortShortages, false, forecast.revision());
            if (proposal.disposition()
                    == AgentItemDispositionProposal.Disposition.TRANSFER_TO_COHORT) {
                log.info("Agent ETC allocation suggestion agent={} item={} quantity={} action=TRANSFER target={} evidence={}",
                        agent.getName(), item.getItemId(), proposal.proposedQuantity(),
                        proposal.target(), proposal.evidence());
                continue;
            }
            if (proposal.disposition()
                    != AgentItemDispositionProposal.Disposition.SELL_SAFE_SURPLUS
                    || proposal.proposedQuantity() <= 0) {
                continue;
            }
            int unreserved = Math.max(0, owned - reserved);
            int quantity = Math.min(item.getQuantity(),
                    Math.min(unreserved, proposal.proposedQuantity()));
            if (quantity <= 0) {
                log.warn("Rejected reserved-item sale proposal agent={} item={} owned={} reserved={} proposed={}",
                        agent.getName(), item.getItemId(), owned, reserved,
                        proposal.proposedQuantity());
                continue;
            }
            log.info("Agent ETC sale {} agent={} item={} quantity={} protected={} revision={} evidence={}",
                    mode == AgentQuestItemSellMode.SHADOW ? "shadow" : "proposal",
                    agent.getName(), item.getItemId(), quantity,
                    proposal.protectedQuantity(), forecast.revision(), proposal.evidence());
            if (mode == AgentQuestItemSellMode.ENFORCED) {
                candidates.add(new AgentEtcSaleCandidate(item, (short) quantity, proposal));
            }
        }
        return List.copyOf(candidates);
    }

    private static Map<String, Integer> cohortShortages(
            AgentRuntimeEntry entry,
            int itemId,
            long nowMs) {
        long cohortId = AgentRelationshipRuntime.cohortId(entry);
        if (cohortId == 0L) {
            return Map.of();
        }
        Map<String, Integer> shortages = new HashMap<>();
        for (AgentRuntimeEntry peer : AgentRuntimeRegistry.entriesForCohort(cohortId)) {
            Character character = peer.bot();
            if (peer == entry || character == null) {
                continue;
            }
            AgentQuestItemDemandForecast peerForecast = AgentQuestItemDemandRuntime.forecast(
                    character, Set.of(), plannedJobs(peer));
            peerForecast.items().stream()
                    .filter(item -> item.itemId() == itemId)
                    .findFirst()
                    .ifPresent(item -> {
                        int ledgerReserved = AgentInventoryReservationRuntime.ledger(peer)
                                .reservedQuantity(itemId, nowMs);
                        int shortage = Math.max(0,
                                Math.max(item.authoritativeDemand(), ledgerReserved)
                                        - item.ownedQuantity());
                        if (shortage > 0) {
                            shortages.put(character.getName(), shortage);
                        }
                    });
        }
        return Map.copyOf(shortages);
    }

    private static Set<Integer> plannedJobs(AgentRuntimeEntry entry) {
        AgentCareerProgressionState state = entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        return state.bundle() == null ? Set.of() : Set.of(state.bundle().firstJobId());
    }

    private static int count(Character agent, int itemId) {
        Inventory inventory = agent.getInventory(InventoryType.ETC);
        return inventory == null ? 0 : inventory.countById(itemId);
    }
}
