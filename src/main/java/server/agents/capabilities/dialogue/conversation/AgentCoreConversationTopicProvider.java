package server.agents.capabilities.dialogue.conversation;

import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicDefinition;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;

import java.util.List;
import java.util.Map;

/** Region-neutral storylets based only on shared runtime and objective state. */
public final class AgentCoreConversationTopicProvider implements AgentConversationTopicProvider {
    public static final String DAILY_LIFE = "daily_life";
    public static final String SLOW_SPAWN = "slow_spawn";
    public static final String QUEST_RELIEF = "quest_relief";

    @Override
    public List<AgentConversationTopicModel> topicModels() {
        return List.of(
                model(AgentDialogueTopicRegistry.QUEST_PROGRESS,
                        "Exchange about current objective progress", Kind.QUEST_PROGRESS),
                model(AgentDialogueTopicRegistry.HUNTING,
                        "Exchange while Agents are hunting", Kind.HUNTING),
                model(AgentDialogueTopicRegistry.TRAVEL,
                        "Exchange about a route or next destination", Kind.TRAVEL),
                model(AgentDialogueTopicRegistry.ENCOURAGEMENT,
                        "Supportive acknowledgement during shared activity", Kind.ENCOURAGEMENT),
                model(DAILY_LIFE,
                        "Low-frequency everyday small talk", Kind.DAILY_LIFE),
                model(SLOW_SPAWN,
                        "Light frustration after a hunting objective takes unusually long", Kind.SLOW_SPAWN),
                model(QUEST_RELIEF,
                        "Relief shortly after completing a long objective", Kind.QUEST_RELIEF));
    }

    private static AgentConversationTopicModel model(String id, String description, Kind kind) {
        return new CoreTopicModel(new AgentDialogueTopicDefinition(id, description), kind);
    }

    private enum Kind {
        QUEST_PROGRESS,
        HUNTING,
        TRAVEL,
        ENCOURAGEMENT,
        DAILY_LIFE,
        SLOW_SPAWN,
        QUEST_RELIEF
    }

    private record CoreTopicModel(AgentDialogueTopicDefinition definition, Kind kind)
            implements AgentConversationTopicModel {
        @Override
        public Map<String, List<String>> templates() {
            return switch (kind) {
                case QUEST_PROGRESS -> Map.of(
                        "ask", List.of(
                                "How's the quest going?", "u nearly done?", "still on this one?",
                                "quest ok so far?", "what do u need next?", "hows ur progress"),
                        "same_objective", List.of(
                                "oh we're on the same quest", "u doing this one too?", "same objective :D",
                                "looks like we need the same thing", "yooo same quest xD", "we both stuck on this huh"),
                        "reply", List.of(
                                "Still working on it.", "getting there", "almost i think",
                                "slowly xD", "a few more i think", "not done yet T_T"),
                        "same_reply", List.of(
                                "yeah lets finish it", "mhm same one", "nice we can do it together",
                                "yup, good luck o/", "same lol", "yea still working on it"));
                case HUNTING -> Map.of(
                        "ask", List.of(
                                "hunting here too?", "u need these mobs too?", "same quest?",
                                "wanna clear this side?", "how many more u need?", "these ur quest mobs?"),
                        "same_hunt", List.of(
                                "we both need these mobs right?", "same hunt quest :D", "u killing these too?",
                                "looks like we're hunting the same thing", "same mobs lol", "lets split the map maybe"),
                        "reply", List.of(
                                "yeah, need a few more", "yup same mobs", "mhm lets clear em",
                                "yea lmao still hunting", "still need a bunch", "almost done i think"),
                        "same_reply", List.of(
                                "yep, good luck", "mhm lets get em", "yeah i need these too",
                                "we got this xD", "same lmao", "yup just a few more"));
                case TRAVEL -> Map.of(
                        "ask", List.of(
                                "you going this way too?", "same next map?", "where u headed?",
                                "which way are u going", "u moving on too?", "where next?"),
                        "same_route", List.of(
                                "think we're going to the same place", "same destination?", "oh same way :D",
                                "looks like our next quest matches", "we going together lol", "same route nice"),
                        "reply", List.of(
                                "yeah, heading onward", "same way i think", "next town for me",
                                "just following the path :D", "not sure, wherever the quest says", "going to the next npc"),
                        "same_reply", List.of(
                                "yeah lets go", "mhm same place", "nice, see u there",
                                "yup this way o/", "same route xD", "yea im heading there too"));
                case ENCOURAGEMENT -> Map.of(
                        "share", List.of(
                                "we got this", "glgl", "almost there", "keep going :D",
                                "nice, lets do it", "good luck o/", "dont give up yet", "ez we can finish"));
                case DAILY_LIFE -> Map.of(
                        "ask", List.of(
                                "how's ur day?", "how u doing", "got school later?", "u busy today?",
                                "what u doing after this?", "hows class been", "anything interesting today?",
                                "did ur friends start playing too?", "yoooo whats up", "o/ hows it going",
                                "u got work later?", "what did u have for lunch lol"),
                        "reply", List.of(
                                "pretty good, u?", "okok, got homework later T_T", "need do chores after this lol",
                                "work later... oh man", "class was kinda long today", "my teacher gave us more work D:",
                                "a friend told me to try this", "family stuff later so i got like 20 mins",
                                "just chilling :D", "not bad, kinda tired", "idk just avoiding my chores xD",
                                "doing good, gonna eat soon"));
                case SLOW_SPAWN -> Map.of(
                        "complain", List.of(
                                "where are the mobs lol", "spawn is so slow T_T", "bruh i still need more",
                                "did someone clear everything? o_O", "these mobs taking forever", "dang no spawns again",
                                "lmao this map is empty", "i only need one more cmon"),
                        "reply", List.of(
                                "same lmao", "they'll spawn soon i hope", "lets check the other side",
                                "yeah this is taking a bit", "hang in there xD", "i need more too T_T",
                                "maybe next spawn", "yea everyone is hunting them"));
                case QUEST_RELIEF -> Map.of(
                        "relief", List.of(
                                "finally done with that one", "oh my gawd finally", "quest done :D",
                                "that took forever lmao", "yesss finished", "omg im free T_T",
                                "got the last one finally", "done done done lets gooo"),
                        "reply", List.of(
                                "niceee", "gz :D", "finally xD", "good job!",
                                "lets gooo", "about time lol", "grats o/", "yooo nice"));
            };
        }

        @Override
        public int utility(AgentConversationSelectionContext context) {
            AgentConversationActivity first = context.firstActivity();
            AgentConversationActivity second = context.secondActivity();
            boolean active = first.objectiveActive() || second.objectiveActive();
            boolean hunting = first.hunting() || second.hunting();
            long objectiveAgeMs = Math.max(first.objectiveAgeMs(), second.objectiveAgeMs());
            boolean recentCompletion = first.recentlyCompleted() || second.recentlyCompleted();
            int jitter = Math.floorMod((int) (context.variationSeed() ^ definition.topicId().hashCode()), 9);
            return switch (kind) {
                case QUEST_RELIEF -> recentCompletion ? 112 + jitter : UNAVAILABLE;
                case SLOW_SPAWN -> hunting && objectiveAgeMs >= 10_000L ? 104 + jitter : UNAVAILABLE;
                case HUNTING -> hunting ? 82 + jitter : UNAVAILABLE;
                case QUEST_PROGRESS -> active ? 62 + jitter : 25 + jitter;
                case TRAVEL -> active ? 48 + jitter : 32 + jitter;
                case ENCOURAGEMENT -> 38 + jitter;
                case DAILY_LIFE -> gate(context.variationSeed(), definition.topicId(), 28)
                        ? 52 + jitter : UNAVAILABLE;
            };
        }

        @Override
        public AgentSemanticDialogueAct produce(AgentConversationModelContext context) {
            boolean reply = context.turnIndex() > 1;
            AgentConversationActivity speaker = AgentConversationActivityRegistry.snapshot(
                    context.speaker(), context.nowMs());
            AgentConversationActivity listener = AgentConversationActivityRegistry.snapshot(
                    context.listener(), context.nowMs());
            boolean sameObjective = !speaker.objectiveKey().isBlank()
                    && speaker.objectiveKey().equals(listener.objectiveKey());
            String actKey = switch (kind) {
                case QUEST_PROGRESS -> reply
                        ? sameObjective ? "same_reply" : "reply"
                        : sameObjective ? "same_objective" : "ask";
                case HUNTING -> reply
                        ? sameObjective ? "same_reply" : "reply"
                        : sameObjective ? "same_hunt" : "ask";
                case TRAVEL -> reply
                        ? sameObjective ? "same_reply" : "reply"
                        : sameObjective ? "same_route" : "ask";
                case DAILY_LIFE -> reply ? "reply" : "ask";
                case ENCOURAGEMENT -> "share";
                case SLOW_SPAWN -> reply ? "reply" : "complain";
                case QUEST_RELIEF -> reply ? "reply" : "relief";
            };
            return act(context, actKey, Map.of());
        }
    }

    static boolean gate(long seed, String salt, int percentage) {
        long mixed = seed ^ (long) salt.hashCode() * 0x9e3779b97f4a7c15L;
        mixed ^= mixed >>> 33;
        return Math.floorMod(mixed, 100L) < percentage;
    }
}
