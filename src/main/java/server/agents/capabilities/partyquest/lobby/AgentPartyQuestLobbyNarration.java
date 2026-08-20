package server.agents.capabilities.partyquest.lobby;

import client.Character;

import java.util.List;
import java.util.Locale;

/** Roster-aware recruitment wording shared by party quests. */
public final class AgentPartyQuestLobbyNarration {
    private AgentPartyQuestLobbyNarration() {
    }

    public static String recruiterMessage(
            AgentPartyQuestLobbyProfile profile,
            List<Character> roster,
            int currentSize,
            long variation) {
        if (profile == null) throw new IllegalArgumentException("Lobby profile is required");
        List<Character> members = roster == null ? List.of() : roster.stream()
                .filter(java.util.Objects::nonNull).toList();
        int current = Math.max(0, currentSize);
        int missing = Math.max(0, profile.maximumPartySize() - current);
        int style = Math.floorMod(variation, 10);
        if (style <= 6) return countMessage(profile.questKey(), current, missing,
                profile.maximumPartySize(), style);
        if (style <= 8) return compositionMessage(
                profile.questKey(), members, current, missing, profile.maximumPartySize());
        return requirementMessage(profile, members, current);
    }

    private static String countMessage(
            String questKey, int current, int missing, int maximum, int style) {
        String activity = questKey.toUpperCase(Locale.ROOT);
        if (missing <= 0) return activity + ' ' + current + '/' + maximum + ", party ready.";
        String need = missing == 1 ? "1 more" : missing + " more";
        return switch (Math.floorMod(style, 3)) {
            case 0 -> activity + ' ' + current + '/' + maximum + ", looking for " + need + '.';
            case 1 -> "Recruiting " + need + " for " + activity + ". Currently "
                    + current + '/' + maximum + '.';
            default -> activity + " party is " + current + '/' + maximum + ". Need " + need + '.';
        };
    }

    private static String compositionMessage(
            String questKey, List<Character> roster, int current, int missing, int maximum) {
        String jobs = roster.stream().map(AgentPartyQuestLobbyNarration::jobName)
                .collect(java.util.stream.Collectors.joining(", "));
        if (jobs.isBlank()) jobs = "roster forming";
        String tail = missing <= 0 ? "Party ready." : "Need "
                + (missing == 1 ? "1 more." : missing + " more.");
        return questKey.toUpperCase(Locale.ROOT) + ' ' + current + '/' + maximum
                + ": " + jobs + ". " + tail;
    }

    private static String requirementMessage(
            AgentPartyQuestLobbyProfile profile, List<Character> roster, int current) {
        List<String> missing = profile.memberRequirements().stream()
                .filter(requirement -> roster.stream().filter(requirement::matches).count()
                        < requirement.minimumCount())
                .map(AgentPartyQuestLobbyProfile.MemberRequirement::description)
                .toList();
        String activity = profile.questKey().toUpperCase(Locale.ROOT);
        if (!missing.isEmpty()) {
            return activity + ' ' + current + '/' + profile.maximumPartySize()
                    + ", looking for " + naturalList(missing) + '.';
        }
        return "Looking for lv" + profile.minimumLevel() + '-' + profile.maximumLevel()
                + " for " + activity + ". Currently " + current + '/'
                + profile.maximumPartySize() + '.';
    }

    private static String naturalList(List<String> values) {
        if (values.size() <= 1) return values.isEmpty() ? "members" : values.getFirst();
        return String.join(", ", values.subList(0, values.size() - 1))
                + " and " + values.getLast();
    }

    private static String jobName(Character member) {
        return member.getJob() == null ? "beginner"
                : member.getJob().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
