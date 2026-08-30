package server.agents.capabilities.expedition;

import server.expeditions.ExpeditionType;

import java.util.List;

/** Immutable lobby contract shared by expedition-specific Agent scenarios. */
public record AgentExpeditionSpec(
        String scenarioId,
        String displayName,
        ExpeditionType expeditionType,
        int entranceMapId,
        int battleMapId,
        int returnMapId,
        int entryNpcId,
        int partyCapacity,
        long readyCountdownMs,
        List<String> memberNames,
        List<Integer> createSelections,
        List<Integer> joinSelections,
        List<Integer> startSelections) {

    public static final int MAX_EXPEDITION_MEMBERS = 30;

    public AgentExpeditionSpec {
        if (scenarioId == null || scenarioId.isBlank() || displayName == null || displayName.isBlank()
                || expeditionType == null || entranceMapId <= 0 || battleMapId <= 0 || returnMapId <= 0
                || entryNpcId <= 0 || partyCapacity < 1 || partyCapacity > 6 || readyCountdownMs < 0L
                || memberNames == null || memberNames.isEmpty()
                || memberNames.size() > MAX_EXPEDITION_MEMBERS
                || memberNames.size() > expeditionType.getMaxSize()
                || memberNames.stream().anyMatch(name -> name == null || name.isBlank())
                || memberNames.stream().distinct().count() != memberNames.size()
                || createSelections == null || joinSelections == null || startSelections == null) {
            throw new IllegalArgumentException("a complete 1-30 member expedition specification is required");
        }
        memberNames = List.copyOf(memberNames);
        createSelections = List.copyOf(createSelections);
        joinSelections = List.copyOf(joinSelections);
        startSelections = List.copyOf(startSelections);
    }

    public int participantCount() {
        return memberNames.size();
    }

    public int partyCount() {
        return (participantCount() + partyCapacity - 1) / partyCapacity;
    }
}
