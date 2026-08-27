package server.agents.progression;

import client.Character;
import client.QuestStatus;
import constants.inventory.ItemConstants;
import constants.game.ExpTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.StringUtil;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** GM observation harness for one Agent on each Explorer second-job branch. */
public final class AgentMushroomKingdomCohortService {
    private static final Logger log = LoggerFactory.getLogger(AgentMushroomKingdomCohortService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomCohortService.SPAWN_STAGGER_MS");
    private static final long ACCELERATION_POLL_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomCohortService.ACCELERATION_POLL_MS");
    private static final int ACCELERATION_SAMPLE_COUNT = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomCohortService.ACCELERATION_SAMPLE_COUNT");
    private static final int RECOMMENDATION_LETTER_ITEM_ID = 4_032_375;
    private static final int KILLER_MUSHROOM_SPORE_ITEM_ID = 2_430_014;
    private static final int ROYAL_SEAL_ITEM_ID = 4_001_318;
    private static final int THORN_REMOVER_ITEM_ID = 2_430_015;
    private static final int WEDDING_HALL_KEY_ITEM_ID = 4_032_388;
    private static final int SECRET_ROOM_KEY_ITEM_ID = 4_032_405;
    private static final int VIOLETTA_TRUTH_ITEM_ID = 4_032_387;
    private static final int PRIME_MINISTER_TRUTH_ITEM_ID = 4_032_386;
    private static final List<Integer> YETI_MOB_IDS = List.of(3_300_005, 3_300_006, 3_300_007);
    private static final List<CohortMember> ROSTER = List.of(
            new CohortMember("SporeFighter", "fighter"),
            new CohortMember("PepePage", "page"),
            new CohortMember("ThornSpear", "spearman"),
            new CohortMember("EmberSpore", "fp-wizard"),
            new CohortMember("FrostSpore", "il-wizard"),
            new CohortMember("HealShroom", "cleric"),
            new CohortMember("ViolettaBow", "hunter"),
            new CohortMember("PepeBolt", "crossbowman"),
            new CohortMember("ShadowSpore", "assassin"),
            new CohortMember("RoyalBandit", "bandit"),
            new CohortMember("MushBrawler", "brawler"),
            new CohortMember("SporeGunner", "gunslinger"));
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();

    private AgentMushroomKingdomCohortService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        String action = params == null || params.length == 0 ? "help" : params[0].toLowerCase();
        try {
            return switch (action) {
                case "start" -> start(operator, seed(params, nowMs), accelerated(params), tenPercent(params),
                        includeSelf(params));
                case "status" -> status(operator);
                case "stop" -> stop(operator);
                case "fill", "complete" -> fillControlledCharacterCondition(operator);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("Mushroom Kingdom cohort command failed", failure);
            return List.of("Mushroom Kingdom cohort failed: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, long seed,
                                      boolean accelerated, boolean tenPercent,
                                      boolean includeSelf) throws Exception {
        ArrayList<String> response = new ArrayList<>();
        if (accelerated && tenPercent) {
            return List.of("Choose either accelerated or ten-percent mode, not both.");
        }
        if (includeSelf && !tenPercent) {
            return List.of("include-self is available only with ten-percent mode.");
        }
        if (includeSelf) {
            int entryQuest = AgentMushroomKingdomCatalog.supportedSecondJob(operator.getJob().getId())
                    ? AgentMushroomKingdomCatalog.entryQuestForJob(operator.getJob().getId()) : 0;
            String validation = controlledParticipantValidation(operator.getLevel(), operator.getJob().getId(),
                    entryQuest == 0 ? QuestStatus.Status.NOT_STARTED.getId()
                            : operator.getQuestStatus(entryQuest),
                    hasMushroomQuestlineProgress(operator));
            if (validation != null) return List.of(validation);
        }
        if (RUNS.containsKey(operator.getId())) response.addAll(stop(operator));
        for (CohortMember member : ROSTER) {
            String failure = PROVISIONING.ensureBackingCharacter(operator, member.name());
            if (failure != null) return List.of(failure);
        }
        Run run = new Run(operator, seed, accelerated, tenPercent, includeSelf);
        RUNS.put(operator.getId(), run);
        if (includeSelf) {
            try {
                prepareControlledParticipant(run, operator);
                scheduleControlledAcceleration(run);
            } catch (RuntimeException failure) {
                RUNS.remove(operator.getId(), run);
                throw failure;
            }
        }
        for (int ordinal = 0; ordinal < ROSTER.size(); ordinal++) {
            CohortMember member = ROSTER.get(ordinal);
            int index = ordinal;
            AgentSchedulerRuntime.schedule(() -> launch(run, member, index), SPAWN_STAGGER_MS * ordinal);
        }
        response.add("Launching 12 level-30 Mushroom Kingdom Agents (seed " + seed + ", "
                + run.modeLabel() + ").");
        if (accelerated) {
            response.add("The cohort must demonstrate " + ACCELERATION_SAMPLE_COUNT
                    + " real drops per large collection and every Agent must loot at least one; "
                    + "the harness then supplies only the remainder. Bosses and story actions stay live.");
        }
        if (tenPercent) {
            response.add("Every Agent starts at the Mushroom Kingdom entrance with its recommendation "
                    + "quest active and letter ready to submit, then must personally complete "
                    + "ceil(10%) of each large item objective. The harness supplies the remainder, then pays "
                    + "9x the demonstrated EXP/meso only after submission. One-off objectives and all three "
                    + "colored Yetis remain fully live. The test deliberately loses and recovers the Killer "
                    + "Mushroom Spore through q2338 and the Royal Seal through q2342.");
        }
        if (includeSelf) {
            response.add(operator.getName() + " is included as a manual 13th participant. The harness moved you "
                    + "to the entrance, activated your recommendation quest, and will apply the same 10%/9x rules; "
                    + "you retain control of all movement, combat, dialogue, item use, and portals.");
        }
        response.add("Six male and six female Agents cover: "
                + ROSTER.stream().map(CohortMember::branchId).toList());
        response.add("Use !mushroomtest status or !mushroomtest stop.");
        return response;
    }

    private static void launch(Run run, CohortMember member, int ordinal) {
        if (RUNS.get(run.operator.getId()) != run) return;
        Character launched = null;
        try {
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                    run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                    AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID);
            Point point = spawnPoint(map, ordinal);
            AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, member.name(), map, point);
            if (!result.success()) throw new IllegalStateException(result.errorMessage());
            launched = result.agent();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
            if (entry == null) throw new IllegalStateException("spawned Agent runtime is unavailable");
            AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(member.branchId());
            AgentMushroomKingdomFixtureService.Prepared prepared =
                    AgentMushroomKingdomFixtureService.prepare(
                            entry, branch, ordinal, mix(run.seed, ordinal), System.currentTimeMillis());
            int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(branch.targetJobId());
            if (run.tenPercent) {
                AgentMapGatewayRuntime.map().changeMapNear(launched, map, point);
                prepareEntranceTurnIn(launched, entryQuest);
            } else {
                MapleMap leaderMap = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                        AgentMushroomKingdomCatalog.entryLeaderMap(entryQuest));
                AgentMapGatewayRuntime.map().changeMapNear(
                        launched, leaderMap, spawnPoint(leaderMap, ordinal));
            }
            AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, launched);
            if (!AgentUniversalPlanRuntime.start(entry, launched, "mushroom-kingdom-questline",
                    AgentPlanStartRequest.EMPTY, System.currentTimeMillis())) {
                throw new IllegalStateException("Mushroom Kingdom plan admission was rejected");
            }
            synchronized (run) {
                run.agentIds.put(member.name(), launched.getId());
                run.prepared.put(member.name(), prepared);
            }
            if (run.accelerated || run.tenPercent) scheduleAcceleration(run, member, entry);
        } catch (Exception failure) {
            if (launched != null) disconnect(launched.getId());
            synchronized (run) { run.failures.put(member.name(), failure.getMessage()); }
            log.warn("Could not launch Mushroom Kingdom fixture {}", member.name(), failure);
        }
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No Mushroom Kingdom cohort is active.");
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Mushroom Kingdom cohort: " + run.agentIds.size() + "/12 launched, seed " + run.seed
                + ", mode " + run.modeLabel() + '.');
        synchronized (run) {
            if (run.includeSelf) {
                Character controlled = AgentCharacterGatewayRuntime.characters()
                        .findOnlineCharacterById(run.operator.getId());
                String state = controlled == null ? "offline" : "map " + controlled.getMapId() + ", "
                        + controlledQuestStatus(controlled);
                lines.add(run.controlledName + " [controlled " + run.controlledBranchId + "]: " + state
                        + (run.accelerationActivity.containsKey(run.controlledName)
                        ? ", " + run.accelerationActivity.get(run.controlledName) : ""));
            }
            for (CohortMember member : ROSTER) {
                Integer id = run.agentIds.get(member.name());
                if (id == null) {
                    lines.add(member.name() + " [" + member.branchId() + "]: "
                            + run.failures.getOrDefault(member.name(), "launch pending"));
                    continue;
                }
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
                Character agent = entry == null ? null : entry.bot();
                AgentMushroomKingdomState state = entry == null ? null : entry.capabilityStates()
                        .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
                AgentMushroomKingdomFixtureService.Prepared fixture = run.prepared.get(member.name());
                lines.add(member.name() + " [" + member.branchId() + ", "
                        + (fixture == null ? "preparing" : fixture.apProfileId()) + "]: "
                        + (agent == null ? "offline" : "map " + agent.getMapId()) + ", "
                        + (state == null ? "plan starting" : state.phase() + " q" + state.currentQuestId()
                        + " - " + state.reason())
                        + (run.accelerationActivity.containsKey(member.name())
                        ? ", " + run.accelerationActivity.get(member.name()) : ""));
            }
        }
        return lines;
    }

    private static List<String> stop(Character operator) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No Mushroom Kingdom cohort is active.");
        List<Integer> ids;
        synchronized (run) { ids = List.copyOf(run.agentIds.values()); }
        ids.forEach(AgentMushroomKingdomCohortService::disconnect);
        return List.of("Stopped the Mushroom Kingdom cohort; backing characters were retained.");
    }

    private static void disconnect(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : entry.bot();
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Point spawnPoint(MapleMap map, int ordinal) {
        Point base = map.getPortal(0) == null ? new Point(0, 0) : map.getPortal(0).getPosition();
        Point candidate = new Point(base.x + ((ordinal % 6) - 2) * 42, base.y);
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
        return grounded == null ? new Point(base) : grounded;
    }

    private static long seed(String[] params, long fallback) {
        if (params != null) {
            for (int index = 1; index < params.length; index++) {
                try { return Long.parseLong(params[index]); }
                catch (NumberFormatException ignored) { }
            }
        }
        return fallback;
    }

    private static boolean accelerated(String[] params) {
        if (params == null) return false;
        for (int index = 1; index < params.length; index++) {
            if ("accelerated".equalsIgnoreCase(params[index])) return true;
        }
        return false;
    }

    private static boolean tenPercent(String[] params) {
        if (params == null) return false;
        for (int index = 1; index < params.length; index++) {
            String token = params[index].toLowerCase();
            if ("ten-percent".equals(token) || "10-percent".equals(token) || "10pct".equals(token)) {
                return true;
            }
        }
        return false;
    }

    static boolean includeSelf(String[] params) {
        if (params == null) return false;
        for (int index = 1; index < params.length; index++) {
            if ("include-self".equalsIgnoreCase(params[index])) return true;
        }
        return false;
    }

    private static void prepareControlledParticipant(Run run, Character controlled) {
        MapleMap entrance = AgentMapGatewayRuntime.map().resolveMap(
                controlled.getWorld(), AgentClientGatewayRuntime.clients().channel(controlled),
                AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID);
        AgentMapGatewayRuntime.map().changeMapNear(controlled, entrance, spawnPoint(entrance, ROSTER.size()));
        prepareEntranceTurnIn(controlled, AgentMushroomKingdomCatalog.entryQuestForJob(
                controlled.getJob().getId()));
        synchronized (run) {
            run.accelerationActivity.put(run.controlledName, "entry recommendation ready to submit");
        }
    }

    private static void scheduleControlledAcceleration(Run run) {
        AgentSchedulerRuntime.schedule(() -> accelerateControlled(run), ACCELERATION_POLL_MS);
    }

    private static void accelerateControlled(Run run) {
        if (RUNS.get(run.operator.getId()) != run || !run.includeSelf) return;
        Character controlled = AgentCharacterGatewayRuntime.characters()
                .findOnlineCharacterById(run.operator.getId());
        if (controlled == null) {
            synchronized (run) {
                run.accelerationActivity.put(run.controlledName, "offline; 10% watcher waiting");
            }
            scheduleControlledAcceleration(run);
            return;
        }
        settlePendingBonuses(run, run.controlledName, controlled);
        if (controlled.getQuestStatus(AgentMushroomKingdomCatalog.FINAL_QUEST_ID)
                == QuestStatus.Status.COMPLETED.getId()) {
            synchronized (run) {
                run.accelerationActivity.put(run.controlledName, "main story complete through q2336");
            }
            return;
        }
        int questId = controlledMainlineQuest(controlled);
        exerciseTenPercentRecoveryQuests(run, run.controlledName, controlled, questId);
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> candidate.questId() == questId)
                .findFirst().orElse(null);
        accelerateTenPercent(run, run.controlledName, controlled, node,
                objective(run.controlledName, questId));
        scheduleControlledAcceleration(run);
    }

    private static void scheduleAcceleration(Run run, CohortMember member,
                                             AgentRuntimeEntry entry) {
        AgentSchedulerRuntime.schedule(entry,
                () -> accelerate(run, member, entry), ACCELERATION_POLL_MS);
    }

    private static void accelerate(Run run, CohortMember member, AgentRuntimeEntry entry) {
        if (RUNS.get(run.operator.getId()) != run || (!run.accelerated && !run.tenPercent)) return;
        Character agent = entry.bot();
        if (agent == null) return;
        AgentMushroomKingdomState state = entry.capabilityStates()
                .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
        if (state == null) {
            scheduleAcceleration(run, member, entry);
            return;
        }
        settlePendingBonuses(run, member.name(), agent);
        if (state.phase() != AgentMushroomKingdomState.Phase.ACTIVE) return;
        exerciseTenPercentRecoveryQuests(run, member.name(), agent, state.currentQuestId());
        int questId = state.currentQuestId();
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> candidate.questId() == questId)
                .findFirst().orElse(null);
        String objective = objective(member.name(), questId);
        if (run.tenPercent) {
            accelerateTenPercent(run, member.name(), agent, node, objective);
            scheduleAcceleration(run, member, entry);
            return;
        }
        if (node != null && node.itemId() > 0
                && agent.getQuestStatus(questId) == QuestStatus.Status.STARTED.getId()
                && !alreadyAccelerated(run, objective)) {
            int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, node.itemId());
            int demonstrated = recordAndTotalDemonstration(run, member, node, owned);
            int topUp = accelerationTopUp(node, owned, demonstrated);
            if (topUp > 0) {
                if (AgentInventoryGatewayRuntime.inventory().addItem(agent, node.itemId(), (short) topUp)) {
                    synchronized (run) {
                        run.acceleratedObjectives.add(objective);
                        run.accelerationActivity.put(member.name(), "accelerated q" + questId + ' '
                                + owned + "->" + node.requiredCount());
                    }
                    log.info("Mushroom Kingdom observation topped up {} quest {} item {} from {} to {}",
                            member.name(), questId, node.itemId(), owned, node.requiredCount());
                } else {
                    synchronized (run) {
                        run.accelerationActivity.put(member.name(), "q" + questId
                                + " top-up waiting for ETC space");
                    }
                }
            }
        }
        scheduleAcceleration(run, member, entry);
    }

    private static void accelerateTenPercent(Run run, String participantName, Character agent,
                                             AgentMushroomKingdomCatalog.QuestNode node,
                                             String objective) {
        if (node == null || agent.getQuestStatus(node.questId()) != QuestStatus.Status.STARTED.getId()) {
            return;
        }
        synchronized (run) {
            run.baselines.putIfAbsent(objective, snapshot(agent));
        }
        if (node.itemId() <= 0 || alreadyAccelerated(run, objective)) return;
        int required = node.requiredCount();
        int threshold = tenPercentRequirement(required);
        int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, node.itemId());
        if (threshold >= required || owned < threshold || owned >= required) return;

        ObjectiveBaseline baseline;
        synchronized (run) { baseline = run.baselines.get(objective); }
        ObjectiveBaseline reached = snapshot(agent);
        int topUp = required - owned;
        if (!AgentInventoryGatewayRuntime.inventory().addItem(agent, node.itemId(), (short) topUp)) {
            synchronized (run) {
                run.accelerationActivity.put(participantName, "q" + node.questId()
                        + " top-up waiting for ETC space");
            }
            return;
        }
        PendingBonus bonus = pendingBonus(baseline, reached, required, threshold);
        synchronized (run) {
            run.acceleratedObjectives.add(objective);
            run.pendingBonuses.put(objective, bonus);
            run.accelerationActivity.put(participantName, "10% q" + node.questId() + ' '
                    + owned + "->" + required + ", pending +" + bonus.experience()
                    + " EXP/+" + bonus.mesos() + " mesos after submission");
        }
        log.info("Mushroom Kingdom 10-percent test topped up {} quest {} item {} from {} to {}; "
                        + "pending bonus exp={} meso={}",
                participantName, node.questId(), node.itemId(), owned, required,
                bonus.experience(), bonus.mesos());
    }

    static void prepareEntranceTurnIn(Character agent, int entryQuestId) {
        Quest entryQuest = Quest.getInstance(entryQuestId);
        int leaderNpcId = AgentMushroomKingdomCatalog.entryLeaderNpc(entryQuestId);
        entryQuest.forceStartWithActions(agent, leaderNpcId);
        if (agent.getQuestStatus(entryQuestId) != QuestStatus.Status.STARTED.getId()) {
            throw new IllegalStateException("could not activate Mushroom Kingdom entry quest "
                    + entryQuestId);
        }
        int letters = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(agent, RECOMMENDATION_LETTER_ITEM_ID);
        if (letters < 1 && !AgentInventoryGatewayRuntime.inventory().addItem(
                agent, RECOMMENDATION_LETTER_ITEM_ID, (short) 1)) {
            throw new IllegalStateException("could not supply the Mushroom Kingdom recommendation letter");
        }
    }

    private static void exerciseTenPercentRecoveryQuests(
            Run run, String participantName, Character agent, int currentQuestId) {
        if (!run.tenPercent) return;
        String sporeRecovery = objective(participantName, 2338);
        if (currentQuestId == 2322 && claimRecoveryLoss(run, sporeRecovery)) {
            removeAll(agent, KILLER_MUSHROOM_SPORE_ITEM_ID);
            synchronized (run) {
                run.accelerationActivity.put(participantName,
                        "removed Killer Mushroom Spore; waiting for q2338 recovery");
            }
        }
        recordRecoveryCompletion(run, participantName, agent, 2338,
                KILLER_MUSHROOM_SPORE_ITEM_ID, "Killer Mushroom Spore");

        String sealRecovery = objective(participantName, 2342);
        if (agent.getQuestStatus(2333) == QuestStatus.Status.COMPLETED.getId()
                && agent.getQuestStatus(2331) == QuestStatus.Status.STARTED.getId()
                && claimRecoveryLoss(run, sealRecovery)) {
            removeAll(agent, ROYAL_SEAL_ITEM_ID);
            synchronized (run) {
                run.accelerationActivity.put(participantName,
                        "removed Royal Seal; waiting for q2342 recovery");
            }
        }
        recordRecoveryCompletion(run, participantName, agent, 2342,
                ROYAL_SEAL_ITEM_ID, "Royal Seal");
    }

    private static void recordRecoveryCompletion(Run run, String participantName, Character agent,
                                                 int questId, int itemId, String itemName) {
        String recovery = objective(participantName, questId);
        if (agent.getQuestStatus(questId) != QuestStatus.Status.COMPLETED.getId()
                || AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, itemId) < 1) {
            return;
        }
        synchronized (run) {
            if (run.recoveryLosses.contains(recovery)
                    && run.recoveryCompletions.add(recovery)) {
                run.accelerationActivity.put(participantName,
                        "completed q" + questId + " and recovered " + itemName);
            }
        }
    }

    private static boolean claimRecoveryLoss(Run run, String recovery) {
        synchronized (run) {
            return run.recoveryLosses.add(recovery);
        }
    }

    private static void removeAll(Character agent, int itemId) {
        int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, itemId);
        if (owned <= 0) return;
        AgentInventoryGatewayRuntime.inventory().removeById(
                agent, ItemConstants.getInventoryType(itemId), itemId, owned, false, false);
    }

    private static void settlePendingBonuses(Run run, String participantName, Character agent) {
        ArrayList<Map.Entry<String, PendingBonus>> payable = new ArrayList<>();
        synchronized (run) {
            for (Map.Entry<String, PendingBonus> entry : run.pendingBonuses.entrySet()) {
                if (!entry.getKey().startsWith(participantName + ':')) continue;
                int questId = Integer.parseInt(entry.getKey().substring(entry.getKey().indexOf(':') + 1));
                if (agent.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId()) {
                    payable.add(entry);
                }
            }
            payable.forEach(entry -> run.pendingBonuses.remove(entry.getKey()));
        }
        for (Map.Entry<String, PendingBonus> entry : payable) {
            PendingBonus bonus = entry.getValue();
            if (bonus.experience() > 0) agent.gainExp(bonus.experience(), false, false);
            if (bonus.mesos() > 0) agent.gainMeso(bonus.mesos(), false);
            synchronized (run) {
                run.accelerationActivity.put(participantName, "paid " + entry.getKey().substring(
                        entry.getKey().indexOf(':') + 1) + ": +" + bonus.experience()
                        + " EXP/+" + bonus.mesos() + " mesos");
            }
        }
    }

    private static boolean alreadyAccelerated(Run run, String objective) {
        synchronized (run) { return run.acceleratedObjectives.contains(objective); }
    }

    private static int recordAndTotalDemonstration(Run run, CohortMember member,
                                                   AgentMushroomKingdomCatalog.QuestNode node,
                                                   int owned) {
        synchronized (run) {
            run.observedCounts.merge(member.name() + ':' + node.questId(),
                    Math.min(ACCELERATION_SAMPLE_COUNT, Math.max(0, owned)), Math::max);
            int total = 0;
            for (CohortMember candidate : ROSTER) {
                total += run.observedCounts.getOrDefault(
                        candidate.name() + ':' + node.questId(), 0);
            }
            return total;
        }
    }

    static int accelerationTopUp(AgentMushroomKingdomCatalog.QuestNode node,
                                 int owned, int demonstratedCount) {
        if (node == null || node.itemId() <= 0 || node.requiredCount() <= ACCELERATION_SAMPLE_COUNT
                || owned <= 0 || demonstratedCount < ACCELERATION_SAMPLE_COUNT
                || owned >= node.requiredCount()) return 0;
        return node.requiredCount() - owned;
    }

    static int tenPercentRequirement(int required) {
        return required <= 0 ? 0 : Math.max(1, (required + 9) / 10);
    }

    private static ObjectiveBaseline snapshot(Character agent) {
        return new ObjectiveBaseline(totalExperience(agent), agent.getMeso());
    }

    private static long totalExperience(Character agent) {
        long total = agent.getExp();
        for (int level = 1; level < agent.getLevel(); level++) {
            total += ExpTable.getExpNeededForLevel(level);
        }
        return total;
    }

    static PendingBonus pendingBonus(ObjectiveBaseline baseline, ObjectiveBaseline reached,
                                     int required, int threshold) {
        if (baseline == null || reached == null || threshold >= required) return PendingBonus.NONE;
        long expGain = Math.max(0L, reached.totalExperience() - baseline.totalExperience());
        long mesoGain = Math.max(0L, (long) reached.mesos() - baseline.mesos());
        return new PendingBonus(saturatedNinefold(expGain), saturatedNinefold(mesoGain));
    }

    private static int saturatedNinefold(long gained) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gained) * 9L);
    }

    private static String objective(String participantName, int questId) {
        return participantName + ':' + questId;
    }

    static String controlledParticipantValidation(int level, int jobId,
                                                  int entryQuestStatus,
                                                  boolean hasQuestlineProgress) {
        if (level != 30) {
            return "include-self requires an exact level-30 test character; current level is " + level + '.';
        }
        if (!AgentMushroomKingdomCatalog.supportedSecondJob(jobId)) {
            return "include-self requires one of the 12 Explorer second jobs; current job is " + jobId + '.';
        }
        if (entryQuestStatus != QuestStatus.Status.NOT_STARTED.getId() || hasQuestlineProgress) {
            return "include-self requires a fresh Mushroom Kingdom quest state. Reset the entry, main-story, "
                    + "q2337/q2338/q2342, and thorn-barrier quest records first.";
        }
        return null;
    }

    private static boolean hasMushroomQuestlineProgress(Character controlled) {
        int notStarted = QuestStatus.Status.NOT_STARTED.getId();
        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            if (controlled.getQuestStatus(node.questId()) != notStarted) return true;
        }
        for (int questId : List.of(2337, 2338, 2342,
                AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)) {
            if (controlled.getQuestStatus(questId) != notStarted) return true;
        }
        return false;
    }

    private static int controlledMainlineQuest(Character controlled) {
        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            if (controlled.getQuestStatus(node.questId()) == QuestStatus.Status.STARTED.getId()) {
                return node.questId();
            }
        }
        return 0;
    }

    private static String controlledQuestStatus(Character controlled) {
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(controlled.getJob().getId());
        if (controlled.getQuestStatus(entryQuest) == QuestStatus.Status.STARTED.getId()) {
            return "entry q" + entryQuest + " active";
        }
        int questId = controlledMainlineQuest(controlled);
        if (questId > 0) return "q" + questId + " active";
        if (controlled.getQuestStatus(AgentMushroomKingdomCatalog.FINAL_QUEST_ID)
                == QuestStatus.Status.COMPLETED.getId()) return "main story complete";
        return "between story quests";
    }

    private static List<String> fillControlledCharacterCondition(Character player) {
        return fillControlledCharacterCondition(player, AgentInventoryGatewayRuntime.inventory());
    }

    static List<String> fillControlledCharacterCondition(Character player, InventoryGateway inventory) {
        if (player == null || inventory == null) return List.of("Character and inventory are required.");
        int jobId = player.getJob().getId();
        if (!AgentMushroomKingdomCatalog.supportedSecondJob(jobId)) {
            return List.of("This helper supports the 12 Explorer second jobs only; current job is " + jobId + '.');
        }
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(jobId);
        int entryStatus = player.getQuestStatus(entryQuest);
        if (entryStatus != QuestStatus.Status.COMPLETED.getId()) {
            if (entryStatus != QuestStatus.Status.STARTED.getId()) {
                return List.of("Mushroom Kingdom entry quest " + entryQuest
                        + " is not active. Start it with your job instructor first.");
            }
            return supplyItem(player, inventory, entryQuest, RECOMMENDATION_LETTER_ITEM_ID, 1,
                    "Return to the Head Patrol Officer at the Mushroom Kingdom entrance.");
        }

        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            int status = player.getQuestStatus(node.questId());
            if (status == QuestStatus.Status.COMPLETED.getId()) continue;
            if (status != QuestStatus.Status.STARTED.getId()) {
                return List.of("Next Mushroom Kingdom quest q" + node.questId()
                        + " is not active. Start it normally; there is no active completion condition to fill.");
            }
            return fillNodeCondition(player, inventory, node);
        }
        return List.of("The Mushroom Kingdom main story is already complete through q2336.");
    }

    private static List<String> fillNodeCondition(Character player, InventoryGateway inventory,
                                                  AgentMushroomKingdomCatalog.QuestNode node) {
        int questId = node.questId();
        if (node.itemId() > 0) {
            return supplyItem(player, inventory, questId, node.itemId(), node.requiredCount(),
                    "The normal quest turn-in can now validate the full item requirement.");
        }
        if (questId == 2330) {
            for (int mobId : YETI_MOB_IDS) setKillProgress(player, questId, mobId, 1);
            return List.of("Filled q2330 kill credit: one kill each for Yetis " + YETI_MOB_IDS + '.');
        }
        if (questId == 2314 || questId == 2322) {
            player.setQuestProgress(questId, questId, "1");
            return List.of("Filled q" + questId + " investigation progress. Return to its completion NPC.");
        }
        if (questId == 2324) {
            return supplyItem(player, inventory, questId, THORN_REMOVER_ITEM_ID, 1,
                    "Use the Thorn Remover beside the right-side barrier; the portal/item script must run.");
        }
        if (questId == 2332) {
            return supplyItem(player, inventory, questId, WEDDING_HALL_KEY_ITEM_ID, 1,
                    "Use the boss-door portal; the portal script must run.");
        }
        if (questId == 2333) {
            setKillProgress(player, questId, 3_300_008, 1);
            return List.of("Filled q2333 Prime Minister kill credit. Submit it to Violetta.");
        }
        if (questId == 2335) {
            return supplyItem(player, inventory, questId, SECRET_ROOM_KEY_ITEM_ID, 1,
                    "Use the secret-room portal; the portal script must run.");
        }
        if (questId == 2336) {
            return supplyFinalTruthItems(player, inventory);
        }
        if (!node.mobIds().isEmpty()) {
            node.mobIds().forEach(mobId -> setKillProgress(player, questId, mobId, node.requiredCount()));
            return List.of("Filled q" + questId + " kill progress to " + node.requiredCount()
                    + " for mobs " + node.mobIds() + '.');
        }
        return List.of("q" + questId + " has no count to fill. Complete its dialogue, travel, item-use, "
                + "or portal action normally.");
    }

    private static List<String> supplyItem(Character player, InventoryGateway inventory,
                                           int questId, int itemId, int required, String instruction) {
        int owned = player.getItemQuantity(itemId, false);
        int missing = Math.max(0, required - owned);
        if (missing > Short.MAX_VALUE) {
            return List.of("q" + questId + " needs " + missing
                    + " more of item " + itemId + ", exceeding one safe command grant.");
        }
        if (missing > 0 && !inventory.addItem(player, itemId, (short) missing)) {
            return List.of("Could not add q" + questId + " item " + itemId
                    + "; free the appropriate inventory slots and retry.");
        }
        String result = "q" + questId + " item " + itemId + ": " + owned + "->" + required + '.';
        return instruction == null || instruction.isBlank()
                ? List.of(result) : List.of(result, instruction);
    }

    private static List<String> supplyFinalTruthItems(Character player, InventoryGateway inventory) {
        ArrayList<String> lines = new ArrayList<>();
        for (int itemId : List.of(VIOLETTA_TRUTH_ITEM_ID, PRIME_MINISTER_TRUTH_ITEM_ID)) {
            int owned = player.getItemQuantity(itemId, false);
            if (owned < 1 && !inventory.addItem(player, itemId, (short) 1)) {
                lines.add("Could not add q2336 item " + itemId
                        + "; free an ETC slot and retry before submission.");
                return lines;
            }
            lines.add("q2336 item " + itemId + ": " + owned + "->1.");
        }
        lines.add("Both final truth items are present; submit q2336 normally.");
        return lines;
    }

    private static void setKillProgress(Character player, int questId, int mobId, int required) {
        player.setQuestProgress(questId, mobId,
                StringUtil.getLeftPaddedStr(Integer.toString(required), '0', 3));
    }

    static boolean hasDurablePostPrimeMinisterEvidence(int quest2333Status,
                                                        int quest2335Status,
                                                        int quest2331Status,
                                                        int quest2336Status) {
        int notStarted = QuestStatus.Status.NOT_STARTED.getId();
        return quest2333Status == QuestStatus.Status.COMPLETED.getId()
                || quest2335Status != notStarted
                || quest2331Status == QuestStatus.Status.COMPLETED.getId()
                || quest2336Status != notStarted;
    }

    private static long mix(long seed, long value) {
        return seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
    }

    private static List<String> help() {
        return List.of("!mushroomtest start [accelerated] [seed] - launch one level-30 Agent per Explorer second job",
                "  accelerated: require 30 real cohort drops and at least one per Agent, then supply the remainder",
                "!mushroomtest start ten-percent [seed] - start all 12 at the entrance with recommendation letters",
                "  submit the entry quest, demonstrate ceil(10%), recover q2338/q2342, and keep Yetis one each",
                "!mushroomtest start ten-percent include-self [seed] - add your fresh level-30 second-job character",
                "  you remain manual while the harness applies the same 10% top-ups and 9x catch-up rewards",
                "!mushroomtest fill - fill the controlled character's current Mushroom Kingdom count/item condition",
                "!mushroomtest status - show each branch, map, quest, and runtime reason",
                "!mushroomtest stop - disconnect the cohort and retain backing characters");
    }

    /** Stable, read-only fixture contract for tooling and regression tests. */
    public static List<CohortMember> roster() { return ROSTER; }

    public record CohortMember(String name, String branchId) { }

    private static final class Run {
        private final Character operator;
        private final long seed;
        private final boolean accelerated;
        private final boolean tenPercent;
        private final boolean includeSelf;
        private final String controlledName;
        private final String controlledBranchId;
        private final Map<String, Integer> agentIds = new LinkedHashMap<>();
        private final Map<String, AgentMushroomKingdomFixtureService.Prepared> prepared = new LinkedHashMap<>();
        private final Map<String, String> failures = new LinkedHashMap<>();
        private final Set<String> acceleratedObjectives = new HashSet<>();
        private final Map<String, String> accelerationActivity = new LinkedHashMap<>();
        private final Map<String, Integer> observedCounts = new LinkedHashMap<>();
        private final Map<String, ObjectiveBaseline> baselines = new LinkedHashMap<>();
        private final Map<String, PendingBonus> pendingBonuses = new LinkedHashMap<>();
        private final Set<String> recoveryLosses = new HashSet<>();
        private final Set<String> recoveryCompletions = new HashSet<>();

        private Run(Character operator, long seed, boolean accelerated, boolean tenPercent,
                    boolean includeSelf) {
            this.operator = operator;
            this.seed = seed;
            this.accelerated = accelerated;
            this.tenPercent = tenPercent;
            this.includeSelf = includeSelf;
            this.controlledName = operator.getName();
            this.controlledBranchId = includeSelf
                    ? AgentSecondJobCatalog.forTargetJob(operator.getJob().getId()).id() : "";
        }

        private String modeLabel() {
            if (tenPercent) return includeSelf
                    ? "10-percent per-Agent + controlled test" : "10-percent per-Agent test";
            return accelerated ? "accelerated observation" : "full objectives";
        }
    }

    record ObjectiveBaseline(long totalExperience, int mesos) { }

    record PendingBonus(int experience, int mesos) {
        private static final PendingBonus NONE = new PendingBonus(0, 0);
    }
}
