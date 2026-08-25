package server.agents.economy.integration.cosmic;

import client.Character;
import client.QuestStatus;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.EconomyEngineConfig;
import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.NpcLocationIndex;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;
import server.quest.Quest;
import server.quest.QuestActionType;
import server.quest.QuestTime;
import server.quest.QuestRequirementType;
import tools.Randomizer;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Starts and turns in generator-approved quests through Cosmic's authoritative quest actions. */
public final class CosmicQuestLifecycleService implements AutonomousFreeMarketBehavior.QuestBehavior {
    private static final Set<QuestActionType> TRANSACTIONAL_ACTIONS = EnumSet.of(
            QuestActionType.EXP, QuestActionType.ITEM, QuestActionType.NEXTQUEST,
            QuestActionType.MESO, QuestActionType.QUEST, QuestActionType.SKILL,
            QuestActionType.FAME, QuestActionType.INFO);
    private static final Set<QuestRequirementType> START_REQUIREMENTS = EnumSet.of(
            QuestRequirementType.JOB, QuestRequirementType.ITEM, QuestRequirementType.QUEST,
            QuestRequirementType.MIN_LEVEL, QuestRequirementType.MAX_LEVEL,
            QuestRequirementType.END_DATE, QuestRequirementType.NPC, QuestRequirementType.INTERVAL,
            QuestRequirementType.COMPLETED_QUEST, QuestRequirementType.MESO);
    private static final Set<QuestRequirementType> COMPLETE_REQUIREMENTS = EnumSet.of(
            QuestRequirementType.JOB, QuestRequirementType.ITEM, QuestRequirementType.QUEST,
            QuestRequirementType.MIN_LEVEL, QuestRequirementType.MAX_LEVEL,
            QuestRequirementType.END_DATE, QuestRequirementType.MOB, QuestRequirementType.NPC,
            QuestRequirementType.INTERVAL, QuestRequirementType.COMPLETED_QUEST,
            QuestRequirementType.MESO);

    private final UUID runId;
    private final EconomyEngineConfig.Quests config;
    private final NamedRandomStreams random;
    private final VictoriaQuestEconomyCatalog catalog;
    private final ItemInformationProvider items;

    public CosmicQuestLifecycleService(UUID runId, EconomyEngineConfig.Quests config,
                                       NamedRandomStreams random, EconomyCatalog economy,
                                       NpcLocationIndex npcLocations) {
        this(runId, config, random, VictoriaQuestEconomyCatalog.fromCosmic(
                        config.catalogResource, config.selectionDisposition,
                        config.victoriaMapCatalogResource, npcLocations, economy,
                        CosmicQuestLifecycleService::supported),
                ItemInformationProvider.getInstance());
    }

    CosmicQuestLifecycleService(UUID runId, EconomyEngineConfig.Quests config,
                                NamedRandomStreams random, VictoriaQuestEconomyCatalog catalog,
                                ItemInformationProvider items) {
        this.runId = Objects.requireNonNull(runId);
        this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random);
        this.catalog = Objects.requireNonNull(catalog);
        this.items = Objects.requireNonNull(items);
    }

    @Override
    public Result advance(Character agent, CommerceParticipant profile, Instant logicalAt) {
        Optional<VictoriaQuestEconomyCatalog.Entry> completion = agent.getStartedQuests().stream()
                .map(status -> catalog.find(status.getQuestID()))
                .flatMap(Optional::stream)
                .filter(entry -> supported(Quest.getInstance(entry.questId())))
                .filter(entry -> logicalCanComplete(agent, entry, logicalAt))
                .min(Comparator.comparingInt(VictoriaQuestEconomyCatalog.Entry::questId));
        if (completion.isPresent()) return complete(agent, profile, completion.orElseThrow(), logicalAt);

        if (agent.getStartedQuests().size() >= config.maximumConcurrentActive) return Result.none();
        double acceptance = config.acceptanceProbabilityPerActivityCycle
                * (0.5d + profile.dailyActivityFraction() * 0.5d);
        if (random.stream("agent." + profile.agentId() + ".quest-acceptance").nextDouble()
                >= acceptance) return Result.none();
        List<VictoriaQuestEconomyCatalog.Entry> starts = catalog.eligibleAtLevel(agent.getLevel()).stream()
                .filter(entry -> supported(Quest.getInstance(entry.questId())))
                .filter(entry -> logicalCanStart(agent, entry, logicalAt))
                .toList();
        if (starts.isEmpty()) return Result.none();
        int selected = random.stream("agent." + profile.agentId() + ".quest-selection")
                .nextInt(starts.size());
        return start(agent, profile, starts.get(selected), logicalAt);
    }

    private Result start(Character agent, CommerceParticipant profile,
                         VictoriaQuestEconomyCatalog.Entry entry, Instant logicalAt) {
        Quest quest = Quest.getInstance(entry.questId());
        if (!execute(agent, profile, entry, logicalAt, EconomyOperationKind.QUEST_START, null, () -> {
            quest.start(agent, entry.startNpcId());
            if (agent.getQuest(quest).getStatus() != QuestStatus.Status.STARTED)
                throw new IllegalStateException("Cosmic rejected quest start " + entry.questId());
        })) return new Result(true, false, "START", entry.questId(), entry.startNpcId(), null,
                Map.of("outcome", "CLIENT_BUSY"));
        return new Result(true, true, "START", entry.questId(), entry.startNpcId(), null,
                Map.of("catalog", catalog.version(), "questName", entry.questName()));
    }

    private Result complete(Character agent, CommerceParticipant profile,
                            VictoriaQuestEconomyCatalog.Entry entry, Instant logicalAt) {
        Quest quest = Quest.getInstance(entry.questId());
        List<Integer> selectable = quest.selectableRewardItemIds(agent);
        Integer selection = selectable.isEmpty() ? null : bestSelection(agent, selectable);
        if (!execute(agent, profile, entry, logicalAt, EconomyOperationKind.QUEST_TURN_IN, selection, () -> {
            quest.complete(agent, entry.completeNpcId(), selection);
            if (agent.getQuest(quest).getStatus() != QuestStatus.Status.COMPLETED)
                throw new IllegalStateException("Cosmic rejected quest completion " + entry.questId());
        })) return new Result(true, false, "TURN_IN", entry.questId(), entry.completeNpcId(), selection,
                Map.of("outcome", "CLIENT_BUSY"));
        return new Result(true, true, "TURN_IN", entry.questId(), entry.completeNpcId(), selection,
                Map.of("catalog", catalog.version(), "questName", entry.questName(),
                        "selectableRewardItemIds", selectable,
                        "selectedRewardItemId", selection == null ? 0 : selectable.get(selection),
                        "rewardSelectionPolicy", config.rewardSelectionPolicy));
    }

    private boolean execute(Character agent, CommerceParticipant profile,
                         VictoriaQuestEconomyCatalog.Entry entry, Instant logicalAt,
                         EconomyOperationKind kind, Integer selection, Runnable mutation) {
        if (!agent.getClient().tryacquireClient()) return false;
        try {
            String action = kind == EconomyOperationKind.QUEST_START ? "START" : "TURN_IN";
            String key = "quest:" + runId + ':' + profile.agentId() + ':' + action + ':'
                    + entry.questId() + ':' + logicalAt;
            NamedRandomStreams.Stream stream = random.stream("agent." + profile.agentId() + ".quest");
            QuestTime.withTimeSource(logicalAt::toEpochMilli, () ->
                    Randomizer.withLongSource(stream::nextLong, () ->
                            EconomyTransactionCoordinator.execute(key, agent, null, kind,
                                    "quest=" + entry.questId() + " npc="
                                            + (kind == EconomyOperationKind.QUEST_START
                                            ? entry.startNpcId() : entry.completeNpcId()), context -> {
                                        mutation.run();
                                        context.recordEvidence("questLifecycle", Map.of(
                                                "questId", entry.questId(), "action", action,
                                                "npcId", kind == EconomyOperationKind.QUEST_START
                                                        ? entry.startNpcId() : entry.completeNpcId(),
                                                "selection", selection == null ? -1 : selection,
                                                "rngStream", "agent." + profile.agentId() + ".quest",
                                                "catalog", catalog.version()));
                                    })));
        } finally {
            agent.getClient().releaseClient();
        }
        return true;
    }

    private int bestSelection(Character agent, List<Integer> itemIds) {
        int selected = 0;
        long best = Long.MIN_VALUE;
        for (int index = 0; index < itemIds.size(); index++) {
            int itemId = itemIds.get(index);
            long wearable = ItemConstants.isEquipment(itemId) && items.meetsEquipRequirements(agent, itemId)
                    ? 1_000_000_000L : 0L;
            long score = wearable + Math.max(0, items.getPrice(itemId, 1));
            if (score > best) { best = score; selected = index; }
        }
        return selected;
    }

    private static boolean supported(Quest quest) {
        return !quest.hasTimeLimit() && !quest.hasScriptRequirement(false)
                && !quest.hasScriptRequirement(true)
                && TRANSACTIONAL_ACTIONS.containsAll(quest.getStartActionTypes())
                && TRANSACTIONAL_ACTIONS.containsAll(quest.getCompleteActionTypes())
                && START_REQUIREMENTS.containsAll(quest.getStartRequirementTypes())
                && COMPLETE_REQUIREMENTS.containsAll(quest.getCompleteRequirementTypes());
    }

    private static boolean logicalCanStart(Character agent, VictoriaQuestEconomyCatalog.Entry entry,
                                           Instant at) {
        return QuestTime.withTimeSource(at::toEpochMilli,
                () -> Quest.getInstance(entry.questId()).canStart(agent, entry.startNpcId()));
    }

    private static boolean logicalCanComplete(Character agent, VictoriaQuestEconomyCatalog.Entry entry,
                                              Instant at) {
        return QuestTime.withTimeSource(at::toEpochMilli,
                () -> Quest.getInstance(entry.questId()).canComplete(agent, entry.completeNpcId()));
    }
}
