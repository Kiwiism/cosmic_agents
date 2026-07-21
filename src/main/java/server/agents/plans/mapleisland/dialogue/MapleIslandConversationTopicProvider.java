package server.agents.plans.mapleisland.dialogue;

import client.Character;
import server.agents.capabilities.dialogue.conversation.AgentConversationModelContext;
import server.agents.capabilities.dialogue.conversation.AgentConversationSelectionContext;
import server.agents.capabilities.dialogue.conversation.AgentConversationTopicModel;
import server.agents.capabilities.dialogue.conversation.AgentConversationTopicProvider;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicDefinition;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;

import java.util.List;
import java.util.Map;

/** Maple Island storylets. They observe quest state but never change objective behavior. */
public final class MapleIslandConversationTopicProvider implements AgentConversationTopicProvider {
    public static final String PIO_BOXES = "maple_island.pio_boxes";
    public static final String RAIN_QUIZ = "maple_island.rain_quiz";
    public static final String YOONA_QUIZ = "maple_island.yoona_quiz";

    private static final int AMHERST_MAP_ID = 1_000_000;
    private static final int TRAINING_CENTER_MAP_ID = 1_010_000;
    private static final int PIO_QUEST_ID = 1_008;
    private static final int PIO_BOARD_ITEM_ID = 4_031_161;
    private static final int PIO_SCREW_ITEM_ID = 4_031_162;
    private static final int RAIN_FIRST_QUEST_ID = 1_009;
    private static final int RAIN_LAST_QUEST_ID = 1_015;
    private static final int YOONA_SHOPPING_QUEST_ID = 8_020;
    private static final int YOONA_LAST_QUIZ_ID = 8_025;
    private static final int YOONA_GUIDE_ITEM_ID = 4_031_180;

    @Override
    public List<AgentConversationTopicModel> topicModels() {
        return List.of(new PioBoxesModel(), new RainQuizModel(), new YoonaQuizModel());
    }

    private static final class PioBoxesModel implements AgentConversationTopicModel {
        private static final AgentDialogueTopicDefinition DEFINITION = new AgentDialogueTopicDefinition(
                PIO_BOXES, "Light conversation while waiting for Pio's recycling boxes");

        @Override
        public AgentDialogueTopicDefinition definition() {
            return DEFINITION;
        }

        @Override
        public Map<String, List<String>> templates() {
            return Map.of(
                    "both_waiting", List.of(
                            "any boxes left around here?", "u waiting for Pio boxes too?",
                            "all the boxes gone again lol", "did beginners break every box already?",
                            "bruh every box is broken", "need a board and screw but theres no boxes T_T"),
                    "have_enough", List.of(
                            "i got enough recycled stuff already", "finally found the board and screw",
                            "done with the boxes :D", "got my Pio items, finally",
                            "i have both items, good luck o/", "finished Pio's box hunt thank gawd"),
                    "not_started", List.of(
                            "Pio asks us to break those wooden boxes", "you havent started Pio's quest yet?",
                            "grab Pio's quest before breaking boxes", "Pio wants an old board and screw",
                            "talk to Pio first then break a box", "u need Pio's quest for those items"),
                    "look_farther", List.of(
                            "maybe there are boxes farther away", "lets check the other side",
                            "some boxes are away from Pio too", "try looking past Lucas, might be one there",
                            "im gonna search farther out", "there are more boxes around Amherst i think"),
                    "reply", List.of(
                            "yeah i'll look around", "okok ty", "same, been waiting a while",
                            "good idea :D", "hope one spawns soon", "ty i'll check there",
                            "same lol everyone got here first", "alright gl finding one"));
        }

        @Override
        public int utility(AgentConversationSelectionContext context) {
            if (!sameMap(context, AMHERST_MAP_ID)) {
                return UNAVAILABLE;
            }
            QuestState first = questState(context.first().bot(), PIO_QUEST_ID);
            QuestState second = questState(context.second().bot(), PIO_QUEST_ID);
            if (first == QuestState.ACTIVE || second == QuestState.ACTIVE) {
                return 125 + jitter(context, PIO_BOXES);
            }
            return first == QuestState.COMPLETE || second == QuestState.COMPLETE
                    ? 58 + jitter(context, PIO_BOXES) : UNAVAILABLE;
        }

        @Override
        public AgentSemanticDialogueAct produce(AgentConversationModelContext context) {
            Character speaker = context.speaker().bot();
            Character listener = context.listener().bot();
            QuestState speakerState = questState(speaker, PIO_QUEST_ID);
            QuestState listenerState = questState(listener, PIO_QUEST_ID);
            String actKey;
            if (context.turnIndex() > 1) {
                actKey = "reply";
            } else if (speakerState == QuestState.COMPLETE || hasPioItems(speaker)) {
                actKey = "have_enough";
            } else if (listenerState == QuestState.NOT_STARTED) {
                actKey = "not_started";
            } else if (Math.floorMod(context.variationSeed(), 3L) == 0L) {
                actKey = "look_farther";
            } else {
                actKey = "both_waiting";
            }
            return act(context, actKey, Map.of("presentationOnly", "true"));
        }
    }

    private static final class RainQuizModel implements AgentConversationTopicModel {
        private static final AgentDialogueTopicDefinition DEFINITION = new AgentDialogueTopicDefinition(
                RAIN_QUIZ, "Low-frequency Amherst conversation around Rain's quiz chain");

        @Override
        public AgentDialogueTopicDefinition definition() {
            return DEFINITION;
        }

        @Override
        public Map<String, List<String>> templates() {
            return Map.of(
                    "ask_answer", List.of(
                            "which answer did u pick?", "uhh whats the answer to this one", "help i clicked the wrong option D:", "does anyone remember this answer?"),
                    "offer_answers", List.of(
                            "i wrote the quiz answers down if anyone needs em", "i finished all 7, can help with answers",
                            "ask me if Rain's quiz gets confusing", "the answers are in Rain's hints btw"),
                    "answer_public", List.of(
                            "Rain answers: I, yes, E, Z, lv8, 5 AP, Southperry",
                            "its I / yes / E / Z / level 8 / 5 AP / Southperry",
                            "quiz list is I, yes, E, Z, lv8, five AP, Southperry",
                            "for all 7: I yes E Z, lvl8, 5 AP, then Southperry"),
                    "starting", List.of(
                            "just started Rain's quizzes", "wait theres seven quizzes?",
                            "time for the Rain quiz chain", "ok quiz 1... lets go",
                            "first Rain quiz, wish me luck", "starting the quiz now :S"),
                    "complain", List.of(
                            "how many quizzes are there omg", "clicked the wrong one again T_T",
                            "another quiz?? lmao", "my reading skills are being tested",
                            "i swear i clicked the right answer D:", "seven questions is kinda a lot lol"),
                    "easy", List.of(
                            "ezpz", "these are easy xD", "quiz speedrun time",
                            "got that one first try :D", "too easy lol", "im flying through these"),
                    "tease", List.of(
                            "u picked that option? xD", "lol not that one", "Rain got u with that question huh",
                            "bruh read it again xD", "its ok i almost clicked that too", "that answer looked right tho lol"),
                    "complete", List.of(
                            "finally finished all Rain quizzes", "all seven done :D",
                            "oh my gawd quiz chain finished", "Rain quizzes complete, lets gooo",
                            "done with every quiz finally", "quiz chain over, im free xD"),
                    "thanks", List.of(
                            "ty for the answer", "thx thx :D", "tyty", "got it, thanks",
                            "tx that helped", "ohhh ok ty", "ty saved me some time", "thanks o/"));
        }

        @Override
        public int utility(AgentConversationSelectionContext context) {
            if (!sameMap(context, AMHERST_MAP_ID)
                    || !gate(context.variationSeed(), RAIN_QUIZ, 18)) {
                return UNAVAILABLE;
            }
            ChainState first = chainState(context.first().bot(), RAIN_FIRST_QUEST_ID, RAIN_LAST_QUEST_ID);
            ChainState second = chainState(context.second().bot(), RAIN_FIRST_QUEST_ID, RAIN_LAST_QUEST_ID);
            if (first.active() || second.active()) {
                return 120 + jitter(context, RAIN_QUIZ);
            }
            return first.completed() || second.completed()
                    ? 64 + jitter(context, RAIN_QUIZ) : 42 + jitter(context, RAIN_QUIZ);
        }

        @Override
        public AgentSemanticDialogueAct produce(AgentConversationModelContext context) {
            ChainState speaker = chainState(context.speaker().bot(), RAIN_FIRST_QUEST_ID, RAIN_LAST_QUEST_ID);
            ChainState listener = chainState(context.listener().bot(), RAIN_FIRST_QUEST_ID, RAIN_LAST_QUEST_ID);
            String actKey;
            if (context.turnIndex() > 1 && listener.completed() && speaker.active()) {
                actKey = "thanks";
            } else if (speaker.completed() && listener.active()) {
                actKey = Math.floorMod(context.variationSeed(), 3L) == 0L
                        ? "answer_public" : "offer_answers";
            } else if (speaker.completed()) {
                actKey = "complete";
            } else if (speaker.active() && listener.completed()) {
                actKey = "ask_answer";
            } else if (!speaker.started() || speaker.completedSteps() == 0) {
                actKey = "starting";
            } else {
                actKey = switch (Math.floorMod(context.variationSeed(), 5)) {
                    case 0 -> "easy";
                    case 1 -> "tease";
                    case 2 -> "ask_answer";
                    default -> "complain";
                };
            }
            return act(context, actKey, Map.of(
                    "presentationOnly", "true",
                    "presentationHint", "quiz_hesitation"));
        }
    }

    private static final class YoonaQuizModel implements AgentConversationTopicModel {
        private static final AgentDialogueTopicDefinition DEFINITION = new AgentDialogueTopicDefinition(
                YOONA_QUIZ, "Low-frequency shopping-guide and quiz conversation near Yoona");

        @Override
        public AgentDialogueTopicDefinition definition() {
            return DEFINITION;
        }

        @Override
        public Map<String, List<String>> templates() {
            return Map.ofEntries(
                    Map.entry("find_guide", List.of(
                            "where do i get Yoona's shopping guide?", "is the shopping guide in Cash Shop?", "been looking everywhere for that shopping list", "how do u get the guide item?")),
                    Map.entry("guide_help", List.of(
                            "go into Cash Shop for the shopping guide", "the shopping guide comes from Cash Shop",
                            "visit Cash Shop then come back to Yoona", "check Cash Shop, thats where i got it",
                            "its sold in Cash Shop for 1 meso", "bottom-right Cash Shop, guide costs one meso")),
                    Map.entry("ask_answer", List.of(
                            "whats the answer to this shopping quiz?", "which option did u choose?",
                            "i picked the wrong answer :S", "help with this Yoona question pls",
                            "anyone remember the shopping answers?", "wrong option again T_T")),
                    Map.entry("offer_answers", List.of(
                            "i finished the chain, can help with answers", "ask if u need a Yoona quiz answer",
                            "i remember the shopping answers", "i can help with all five quizzes")),
                    Map.entry("answer_public", List.of(
                            "Yoona answers: yes, yes, 30 days, once, 1h sword",
                            "shopping answers are yes / yes / 30 days / once / 1h sword",
                            "all five: yes yes, 30 days, once, one-handed sword",
                            "answer list: yes, yes, thirty days, once, then 1h sword")),
                    Map.entry("starting", List.of(
                            "starting Yoona's quiz now", "first shopping question... lets go",
                            "wait theres five shopping quizzes?", "ok got the guide, quiz time :S")),
                    Map.entry("complain", List.of(
                            "so many shopping questions T_T", "another quiz omg",
                            "took me forever to find that guide", "clicked wrong again lmao",
                            "why did the guide take me so long to find", "five questions bruh")),
                    Map.entry("easy", List.of(
                            "shopping quiz ezpz", "this one is easy :D", "got it first try xD", "quiz speedrun lets go")),
                    Map.entry("tease", List.of(
                            "u clicked that one? xD", "lol Yoona got u with that question",
                            "not that option bruh", "ok that answer was tempting tho xD")),
                    Map.entry("complete", List.of(
                            "finally done with Yoona's quizzes", "all shopping quizzes complete :D", "oh my gawd finally finished", "Yoona chain done, lets gooo")),
                    Map.entry("thanks", List.of(
                            "ty for the help", "thx :D", "tyty got it", "thanks, found the guide", "tx that worked")));
        }

        @Override
        public int utility(AgentConversationSelectionContext context) {
            if (!sameMap(context, TRAINING_CENTER_MAP_ID)
                    || !gate(context.variationSeed(), YOONA_QUIZ, 18)) {
                return UNAVAILABLE;
            }
            ChainState first = chainState(context.first().bot(), YOONA_SHOPPING_QUEST_ID, YOONA_LAST_QUIZ_ID);
            ChainState second = chainState(context.second().bot(), YOONA_SHOPPING_QUEST_ID, YOONA_LAST_QUIZ_ID);
            if (first.active() || second.active()) {
                return 122 + jitter(context, YOONA_QUIZ);
            }
            return first.completed() || second.completed()
                    ? 66 + jitter(context, YOONA_QUIZ) : 44 + jitter(context, YOONA_QUIZ);
        }

        @Override
        public AgentSemanticDialogueAct produce(AgentConversationModelContext context) {
            Character speakerCharacter = context.speaker().bot();
            Character listenerCharacter = context.listener().bot();
            ChainState speaker = chainState(speakerCharacter, YOONA_SHOPPING_QUEST_ID, YOONA_LAST_QUIZ_ID);
            ChainState listener = chainState(listenerCharacter, YOONA_SHOPPING_QUEST_ID, YOONA_LAST_QUIZ_ID);
            boolean speakerNeedsGuide = questState(speakerCharacter, YOONA_SHOPPING_QUEST_ID) == QuestState.ACTIVE
                    && speakerCharacter.getItemQuantity(YOONA_GUIDE_ITEM_ID, false) == 0;
            boolean listenerKnowsGuide = questState(listenerCharacter, YOONA_SHOPPING_QUEST_ID) == QuestState.COMPLETE
                    || listenerCharacter.getItemQuantity(YOONA_GUIDE_ITEM_ID, false) > 0;
            String actKey;
            if (context.turnIndex() > 1 && (listener.completed() || listenerKnowsGuide)) {
                actKey = "thanks";
            } else if (speakerNeedsGuide) {
                actKey = "find_guide";
            } else if (listener.active() && listenerCharacter.getItemQuantity(YOONA_GUIDE_ITEM_ID, false) == 0
                    && (speaker.completed() || speakerCharacter.getItemQuantity(YOONA_GUIDE_ITEM_ID, false) > 0)) {
                actKey = "guide_help";
            } else if (speaker.completed() && listener.active()) {
                actKey = Math.floorMod(context.variationSeed(), 3L) == 0L
                        ? "answer_public" : "offer_answers";
            } else if (speaker.completed()) {
                actKey = "complete";
            } else if (listener.completed()) {
                actKey = "ask_answer";
            } else if (!speaker.started() || speaker.completedSteps() <= 1) {
                actKey = "starting";
            } else {
                actKey = switch (Math.floorMod(context.variationSeed(), 5)) {
                    case 0 -> "easy";
                    case 1 -> "tease";
                    case 2 -> "ask_answer";
                    default -> "complain";
                };
            }
            return act(context, actKey, Map.of(
                    "presentationOnly", "true",
                    "presentationHint", speakerNeedsGuide ? "cash_shop_search" : "quiz_hesitation"));
        }
    }

    private static boolean sameMap(AgentConversationSelectionContext context, int mapId) {
        return context.first() != null && context.second() != null
                && context.first().bot() != null && context.second().bot() != null
                && context.first().bot().getMapId() == mapId
                && context.second().bot().getMapId() == mapId;
    }

    private static boolean hasPioItems(Character character) {
        return character.getItemQuantity(PIO_BOARD_ITEM_ID, false) > 0
                && character.getItemQuantity(PIO_SCREW_ITEM_ID, false) > 0;
    }

    private static QuestState questState(Character character, int questId) {
        byte status = character.getQuestStatus(questId);
        return status >= 2 ? QuestState.COMPLETE : status == 1 ? QuestState.ACTIVE : QuestState.NOT_STARTED;
    }

    private static ChainState chainState(Character character, int firstQuestId, int lastQuestId) {
        boolean started = false;
        boolean active = false;
        boolean completed = true;
        int completedSteps = 0;
        for (int questId = firstQuestId; questId <= lastQuestId; questId++) {
            QuestState state = questState(character, questId);
            started |= state != QuestState.NOT_STARTED;
            active |= state == QuestState.ACTIVE;
            completed &= state == QuestState.COMPLETE;
            completedSteps += state == QuestState.COMPLETE ? 1 : 0;
        }
        return new ChainState(started, active, completed, completedSteps);
    }

    private static boolean gate(long seed, String salt, int percentage) {
        long mixed = seed ^ (long) salt.hashCode() * 0x9e3779b97f4a7c15L;
        mixed ^= mixed >>> 33;
        return Math.floorMod(mixed, 100L) < percentage;
    }

    private static int jitter(AgentConversationSelectionContext context, String salt) {
        return Math.floorMod((int) (context.variationSeed() ^ salt.hashCode()), 9);
    }

    private enum QuestState {
        NOT_STARTED,
        ACTIVE,
        COMPLETE
    }

    private record ChainState(boolean started, boolean active, boolean completed, int completedSteps) {
    }
}
