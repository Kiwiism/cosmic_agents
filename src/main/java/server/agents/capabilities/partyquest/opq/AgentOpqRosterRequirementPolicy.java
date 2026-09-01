package server.agents.capabilities.partyquest.opq;

import client.Character;
import client.Job;

import java.util.ArrayList;
import java.util.List;

/** Six-member OPQ coverage, including all-job blessing and Lounge damage branches. */
public final class AgentOpqRosterRequirementPolicy {
    private AgentOpqRosterRequirementPolicy() { }

    public static Coverage evaluate(List<Character> members) {
        List<Character> roster = members == null ? List.of() : members.stream()
                .filter(java.util.Objects::nonNull).toList();
        List<String> missing = new ArrayList<>();
        if (roster.size() != AgentOpqDefinition.PARTY_SIZE) missing.add("exactly six members");
        requireBranch(roster, Job.WARRIOR, "warrior", missing);
        requireBranch(roster, Job.MAGICIAN, "magician", missing);
        requireBranch(roster, Job.BOWMAN, "bowman", missing);
        requireBranch(roster, Job.THIEF, "thief", missing);
        requireBranch(roster, Job.PIRATE, "pirate", missing);
        long magic = roster.stream().filter(AgentOpqRosterRequirementPolicy::magicAttacker).count();
        long physical = roster.stream().filter(character -> !magicAttacker(character)).count();
        if (magic == 0) missing.add("magic damage");
        if (physical == 0) missing.add("physical damage");
        return new Coverage(missing.isEmpty(), List.copyOf(missing));
    }

    public static boolean magicAttacker(Character character) {
        return character != null && character.getJob() != null && character.getJob().isA(Job.MAGICIAN);
    }

    private static void requireBranch(List<Character> roster, Job branch, String name, List<String> missing) {
        if (roster.stream().noneMatch(character -> character.getJob() != null
                && character.getJob().isA(branch))) missing.add(name);
    }

    public record Coverage(boolean complete, List<String> missingRequirements) { }
}
