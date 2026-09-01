package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.Job;

import java.util.ArrayList;
import java.util.List;

/** Exact five-Explorer-family coverage used by autonomous and mixed EPQ parties. */
public final class AgentEpqRosterRequirementPolicy {
    public static final int PARTY_SIZE = 5;
    private static final List<Job> BRANCHES = List.of(
            Job.WARRIOR, Job.MAGICIAN, Job.BOWMAN, Job.THIEF, Job.PIRATE);

    private AgentEpqRosterRequirementPolicy() { }

    public static Coverage evaluate(List<Character> members) {
        List<Character> roster = members == null ? List.of() : members.stream()
                .filter(java.util.Objects::nonNull).toList();
        List<String> missing = new ArrayList<>();
        if (roster.size() != PARTY_SIZE) missing.add("exactly five members");
        for (Job branch : BRANCHES) {
            long count = roster.stream().filter(member -> branch(member) == branch).count();
            if (count == 0) missing.add(branch.name().toLowerCase());
            else if (count > 1) missing.add("only one " + branch.name().toLowerCase());
        }
        return new Coverage(missing.isEmpty(), missing);
    }

    public static Job branch(Character character) {
        Job job = character == null ? null : character.getJob();
        if (job == null) return null;
        return BRANCHES.stream().filter(job::isA).findFirst().orElse(null);
    }

    public static List<Job> branches() {
        return BRANCHES;
    }

    public record Coverage(boolean complete, List<String> missingRequirements) {
        public Coverage {
            missingRequirements = List.copyOf(missingRequirements == null
                    ? List.of() : missingRequirements);
        }
    }
}
