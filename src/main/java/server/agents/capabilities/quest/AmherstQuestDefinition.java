package server.agents.capabilities.quest;

import java.util.EnumSet;
import java.util.Set;

public record AmherstQuestDefinition(
        int questId,
        String questName,
        AmherstNpcRef startNpc,
        AmherstNpcRef completeNpc,
        AmherstQuestCompletionType completionType,
        AmherstQuestSegment segment,
        AmherstQuestPattern pattern,
        Set<AmherstQuestFlag> flags) {

    public AmherstQuestDefinition {
        flags = Set.copyOf(flags);
    }

    static AmherstQuestDefinition npc(int questId, String questName, AmherstNpcRef startNpc, AmherstNpcRef completeNpc,
            AmherstQuestSegment segment, AmherstQuestPattern pattern, AmherstQuestFlag... flags) {
        return new AmherstQuestDefinition(questId, questName, startNpc, completeNpc,
                AmherstQuestCompletionType.NPC, segment, pattern, flagSet(flags));
    }

    static AmherstQuestDefinition autoComplete(int questId, String questName, AmherstNpcRef startNpc,
            AmherstQuestSegment segment, AmherstQuestPattern pattern, AmherstQuestFlag... flags) {
        return new AmherstQuestDefinition(questId, questName, startNpc, new AmherstNpcRef(0, "auto-complete"),
                AmherstQuestCompletionType.AUTO_COMPLETE, segment, pattern, flagSet(flags));
    }

    private static Set<AmherstQuestFlag> flagSet(AmherstQuestFlag... flags) {
        if (flags.length == 0) {
            return Set.of();
        }
        EnumSet<AmherstQuestFlag> set = EnumSet.noneOf(AmherstQuestFlag.class);
        for (AmherstQuestFlag flag : flags) {
            set.add(flag);
        }
        return set;
    }
}
