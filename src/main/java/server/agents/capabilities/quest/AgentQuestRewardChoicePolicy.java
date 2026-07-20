package server.agents.capabilities.quest;

import client.Character;
import client.inventory.InventoryType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.ItemInformationProvider;
import server.StatEffect;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.quest.Quest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Explicit selection policy for non-scripted quests with selectable item rewards. */
public final class AgentQuestRewardChoicePolicy {
    private static final String RESOURCE = "/agents/catalogs/quest-reward-choice-policy.json";
    private static final AgentQuestRewardChoiceCatalog CATALOG = load();

    public record Decision(int selectionIndex, int itemId, String strategy) {
    }

    private AgentQuestRewardChoicePolicy() {
    }

    public static Optional<Decision> choose(Character agent, Quest quest) {
        if (agent == null || quest == null) {
            return Optional.empty();
        }
        List<Integer> options = quest.selectableRewardItemIds(agent);
        if (options.isEmpty()) {
            return Optional.empty();
        }
        Integer fixed = CATALOG.fixedRewardItemByQuestId().get((int) quest.getId());
        int fixedIndex = fixed == null ? -1 : options.indexOf(fixed);
        if (fixedIndex >= 0) {
            return Optional.of(new Decision(fixedIndex, fixed, "authored-fixed-choice"));
        }
        return options.stream()
                .map(itemId -> new Scored(options.indexOf(itemId), itemId, score(agent, itemId)))
                .max(Comparator.comparingLong(Scored::score)
                        .thenComparingInt(scored -> -scored.selectionIndex()))
                .map(scored -> new Decision(scored.selectionIndex(), scored.itemId(),
                        strategy(agent, scored.itemId())));
    }

    static AgentQuestRewardChoiceCatalog catalog() {
        return CATALOG;
    }

    private static long score(Character agent, int itemId) {
        InventoryType type = constants.inventory.ItemConstants.getInventoryType(itemId);
        long marketValue = marketValue(itemId);
        if (type == InventoryType.EQUIP) {
            return 3_000_000_000L + marketValue;
        }
        StatEffect effect = effect(itemId);
        if (AgentUseItemClassificationPolicy.isRecoveryPotion(effect)) {
            long urgency = 0L;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry != null) {
                AgentResourcePlanningState planning = entry.capabilityStates().require(
                        AgentResourcePlanningState.STATE_KEY);
                if (effect.getHp() > 0 || effect.getHpRate() > 0) {
                    urgency = Math.max(urgency, urgency(planning.need(AgentResourceCategory.HP_POTION)));
                }
                if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                    urgency = Math.max(urgency, urgency(planning.need(AgentResourceCategory.MP_POTION)));
                }
            }
            return 2_000_000_000L + urgency * 10_000_000L + marketValue;
        }
        return 1_000_000_000L + marketValue;
    }

    private static long urgency(AgentSupplyNeed need) {
        return need == null ? 0L : need.urgency().ordinal();
    }

    private static String strategy(Character agent, int itemId) {
        InventoryType type = constants.inventory.ItemConstants.getInventoryType(itemId);
        if (type == InventoryType.EQUIP) {
            return "job-eligible-equipment-then-market-value";
        }
        if (AgentUseItemClassificationPolicy.isRecoveryPotion(effect(itemId))) {
            return "current-supply-need-then-market-value";
        }
        return "market-value";
    }

    private static AgentQuestRewardChoiceCatalog load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentQuestRewardChoicePolicy.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing quest reward policy: " + RESOURCE);
            }
            return mapper.readValue(input, AgentQuestRewardChoiceCatalog.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load quest reward policy: " + RESOURCE, failure);
        }
    }

    private static long marketValue(int itemId) {
        try {
            return Math.max(0, ItemInformationProvider.getInstance().getWholePrice(itemId));
        } catch (RuntimeException | LinkageError unavailableCatalog) {
            return 0L;
        }
    }

    private static StatEffect effect(int itemId) {
        try {
            return AgentUseItemClassificationPolicy.itemEffect(itemId);
        } catch (RuntimeException | LinkageError unavailableCatalog) {
            return null;
        }
    }

    private record Scored(int selectionIndex, int itemId, long score) {
    }
}
