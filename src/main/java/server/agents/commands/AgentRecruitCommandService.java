package server.agents.commands;

import client.Character;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentRecruitCommandService {
    private static final Pattern RECRUIT_PATTERN = Pattern.compile(
            "\\b(recruit|adopt|hire|claim)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);

    private AgentRecruitCommandService() {
    }

    public record Hooks(RecruitAction recruitAction,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface RecruitAction {
        String recruit(int leaderCharId, Character leader, String agentName);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static boolean handleRecruitCommand(Character leader, String message, Hooks hooks) {
        Matcher matcher = RECRUIT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return false;
        }

        String name = matcher.group(2);
        String error = hooks.recruitAction().recruit(leader.getId(), leader, name);
        if (error == null) {
            hooks.leaderMessage().send(leader, "Bot '" + name + "' recruited!");
        } else {
            hooks.leaderMessage().send(leader, error);
        }
        return true;
    }
}
