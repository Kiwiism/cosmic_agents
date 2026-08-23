package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.progression.questcatalog.AgentQuestCatalogRepository;
import server.agents.progression.questcatalog.AgentQuestDefinition;
import server.agents.progression.questcatalog.AgentQuestSelectionDisposition;
import server.quest.Quest;

import java.util.Comparator;
import java.util.List;

/** Read-only Director projection of individual quests supported by the Victoria scheduler. */
public final class AgentVictoriaIndividualQuestCatalog {
    private AgentVictoriaIndividualQuestCatalog() {
    }

    public static List<Option> available(Character agent) {
        if (agent == null || agent.getLevel() < 15 || agent.getLevel() >= 30) {
            return List.of();
        }
        AgentQuestCatalogRepository guidance = AgentQuestCatalogRepository.defaultRepository();
        return AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository()
                .eligibleAtLevel(agent.getLevel()).stream()
                .filter(entry -> supportedForAgent(agent, guidance.find(entry.questId()).orElse(null)))
                .filter(entry -> liveStartableOrActive(agent, entry))
                .map(entry -> option(agent, entry))
                .filter(option -> option.status() != QuestStatus.Status.COMPLETED.getId())
                .sorted(Comparator
                        .comparingInt((Option option) ->
                                option.status() == QuestStatus.Status.STARTED.getId() ? 0 : 1)
                        .thenComparingInt(option -> option.localStart() ? 0 : 1)
                        .thenComparingInt(option -> Math.abs(
                                agent.getLevel() - option.recommendedLevel()))
                        .thenComparingInt(Option::questId))
                .toList();
    }

    private static boolean liveStartableOrActive(
            Character agent, AgentVictoriaQuestRuntimeCatalog.Entry entry) {
        int status = agent.getQuestStatus(entry.questId());
        if (status == QuestStatus.Status.STARTED.getId()) {
            return true;
        }
        Quest quest = Quest.getInstance(entry.questId());
        // Lightweight Director projections used by tooling/tests may not materialize the
        // authoritative QuestStatus object. Production Characters always do; fail open only
        // for that incomplete projection and let the scheduler perform the final admission.
        return agent.getQuest(quest) == null || quest.canStart(agent, entry.startNpcId());
    }

    private static boolean supportedForAgent(Character agent, AgentQuestDefinition quest) {
        if (quest == null || !quest.autonomousStartAllowed()
                || quest.selectionDisposition() != AgentQuestSelectionDisposition.ELIGIBLE) {
            return false;
        }
        if (!quest.allowedJobIds().isEmpty()
                && !quest.allowedJobIds().contains(agent.getJob().getId())) {
            return false;
        }
        return quest.prerequisites().stream().allMatch(prerequisite ->
                agent.getQuestStatus(prerequisite.questId()) >= prerequisite.requiredState());
    }

    private static Option option(
            Character agent, AgentVictoriaQuestRuntimeCatalog.Entry entry) {
        AgentQuestDefinition guidance = AgentQuestCatalogRepository.defaultRepository()
                .find(entry.questId()).orElseThrow();
        return new Option(entry.questId(), entry.questName(),
                agent.getQuestStatus(entry.questId()), guidance.recommendedLevel(),
                entry.startMapIds().contains(agent.getMapId()),
                guidance.recommendationRationale());
    }

    public record Option(
            int questId,
            String questName,
            int status,
            int recommendedLevel,
            boolean localStart,
            String rationale) {
        public Option {
            questName = questName == null ? "" : questName.trim();
            rationale = rationale == null ? "" : rationale.trim();
            if (questId <= 0 || questName.isEmpty() || recommendedLevel <= 0) {
                throw new IllegalArgumentException("complete individual quest option is required");
            }
        }
    }
}
