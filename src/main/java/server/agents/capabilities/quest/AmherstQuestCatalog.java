package server.agents.capabilities.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AmherstQuestCatalog {
    public static final int START_MAP_ID = 10000;
    public static final int FINAL_MAP_ID = 1000000;
    public static final int SHANKS_NPC_ID = 22000;

    private static final AmherstNpcRef HEENA = new AmherstNpcRef(2101, "Heena");
    private static final AmherstNpcRef SERA = new AmherstNpcRef(2100, "Sera");
    private static final AmherstNpcRef ROGER = new AmherstNpcRef(2000, "Roger");
    private static final AmherstNpcRef NINA = new AmherstNpcRef(2102, "Nina");
    private static final AmherstNpcRef SEN = new AmherstNpcRef(2001, "Sen");
    private static final AmherstNpcRef SAM = new AmherstNpcRef(2005, "Sam");
    private static final AmherstNpcRef MARIA = new AmherstNpcRef(2103, "Maria");
    private static final AmherstNpcRef LUCAS = new AmherstNpcRef(12000, "Lucas");
    private static final AmherstNpcRef RAIN = new AmherstNpcRef(12101, "Rain");
    private static final AmherstNpcRef PIO = new AmherstNpcRef(10000, "Pio");

    private static final List<AmherstQuestDefinition> QUESTS = List.copyOf(List.of(
            AmherstQuestDefinition.npc(1000, "Borrowing Sera's Mirror", HEENA, SERA,
                    AmherstQuestSegment.MUSHROOM_TOWN, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1001, "Bringing a Mirror to Heena", SERA, HEENA,
                    AmherstQuestSegment.MUSHROOM_TOWN, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1031, "Heena and Sera", HEENA, SERA,
                    AmherstQuestSegment.MUSHROOM_TOWN, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1021, "Roger's Apple", ROGER, ROGER,
                    AmherstQuestSegment.SNAIL_GARDEN, AmherstQuestPattern.ITEM_USE, AmherstQuestFlag.ITEM_USE),
            AmherstQuestDefinition.npc(1003, "What Sen wants to eat", NINA, SEN,
                    AmherstQuestSegment.SNAIL_FIELD, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1004, "Returning to Nina", SEN, NINA,
                    AmherstQuestSegment.SNAIL_FIELD, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1032, "Nina's Brother Sen", NINA, SEN,
                    AmherstQuestSegment.SNAIL_FIELD, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1033, "What Sen Wants", SEN, NINA,
                    AmherstQuestSegment.SNAIL_FIELD, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1034, "Tasty Mushroom Candy", NINA, SEN,
                    AmherstQuestSegment.SNAIL_FIELD, AmherstQuestPattern.ITEM_TURN_IN, AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.npc(1029, "Sam's Advice", SAM, SAM,
                    AmherstQuestSegment.DANGEROUS_FOREST, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(1037, "Help Hunt the Snails", SAM, MARIA,
                    AmherstQuestSegment.DANGEROUS_FOREST, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE),
            AmherstQuestDefinition.npc(1038, "Maria's Letter", MARIA, LUCAS,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1005, "Letter for Lucas", MARIA, LUCAS,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1006, "Lucas' Reply", LUCAS, MARIA,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.NPC_DELIVERY, AmherstQuestFlag.DELIVERY),
            AmherstQuestDefinition.npc(1025, "Maria's Nutritious Juice", MARIA, MARIA,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.ITEM_TURN_IN, AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.autoComplete(1030, "Maria's Map Reading", MARIA,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.AUTO_COMPLETE, AmherstQuestFlag.AUTO_COMPLETE),
            quiz(1009, "Rain's Maple Quiz 1"),
            quiz(1010, "Rain's Maple Quiz 2"),
            quiz(1011, "Rain's Maple Quiz 3"),
            quiz(1012, "Rain's Maple Quiz 4"),
            quiz(1013, "Rain's Maple Quiz 5"),
            quiz(1014, "Rain's Maple Quiz 6"),
            quiz(1015, "Rain's Maple Quiz 7"),
            AmherstQuestDefinition.npc(1008, "Pio's Collecting Recycled Goods", PIO, PIO,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.REACTOR_COLLECTION, AmherstQuestFlag.REACTOR,
                    AmherstQuestFlag.ITEM_TURN_IN),
            AmherstQuestDefinition.npc(1020, "Pio and the Recycling", PIO, PIO,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.NPC_DELIVERY),
            AmherstQuestDefinition.npc(8031, "Protect Lucas's Farm", LUCAS, LUCAS,
                    AmherstQuestSegment.AMHERST, AmherstQuestPattern.KILL_AND_REPORT,
                    AmherstQuestFlag.KILL_OBJECTIVE)));

    private static final Map<Integer, AmherstQuestDefinition> QUESTS_BY_ID = QUESTS.stream()
            .collect(Collectors.toUnmodifiableMap(AmherstQuestDefinition::questId, Function.identity()));

    private AmherstQuestCatalog() {
    }

    public static List<AmherstQuestDefinition> allRequiredQuests() {
        return QUESTS;
    }

    public static List<Integer> requiredQuestIds() {
        return QUESTS.stream()
                .map(AmherstQuestDefinition::questId)
                .sorted()
                .toList();
    }

    public static Optional<AmherstQuestDefinition> find(int questId) {
        return Optional.ofNullable(QUESTS_BY_ID.get(questId));
    }

    public static boolean isRequiredQuest(int questId) {
        return QUESTS_BY_ID.containsKey(questId);
    }

    public static Set<Integer> requiredQuestIdSet() {
        return QUESTS_BY_ID.keySet();
    }

    public static List<AmherstQuestDefinition> routeOrder() {
        List<AmherstQuestDefinition> ordered = new ArrayList<>(QUESTS);
        ordered.sort(Comparator.comparing(AmherstQuestDefinition::segment)
                .thenComparingInt(AmherstQuestDefinition::questId));
        return List.copyOf(ordered);
    }

    private static AmherstQuestDefinition quiz(int questId, String questName) {
        return AmherstQuestDefinition.npc(questId, questName, RAIN, RAIN,
                AmherstQuestSegment.AMHERST, AmherstQuestPattern.QUIZ, AmherstQuestFlag.QUIZ);
    }
}
