package server.agents.progression;

import client.Character;
import client.QuestStatus;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentCombatTargetTraceRuntime;
import server.agents.capabilities.combat.AgentCombatTargetTraceSnapshot;
import server.agents.capabilities.navigation.AgentNavigationTraceRuntime;
import server.agents.capabilities.navigation.AgentNavigationTraceSnapshot;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.population.AgentPopulationAdminService;
import server.agents.population.AgentPopulationRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Explicit developer entry point for running one real Victoria checkpoint against a live server.
 * Normal server startup never invokes this class.
 */
public final class AgentVictoriaLiveValidationRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentVictoriaLiveValidationRunner.class);
    private static final int ORANGE_QUEST_ID = 28_276;
    private static final int SLIME_QUEST_ID = 28_277;
    private static final int PIG_QUEST_ID = 28_278;
    private static final int RIBBON_QUEST_ID = 28_279;
    private static final int ORANGE_MOB_ID = 1_210_102;
    private static final int SLIME_MOB_ID = 210_100;
    private static final int PIG_MOB_ID = 1_210_100;
    private static final int PIG_RIBBON_ITEM_ID = 4_000_002;
    private static final long SPAWN_TIMEOUT_MS = Duration.ofMinutes(2).toMillis();
    private static final long SAMPLE_INTERVAL_MS = Duration.ofSeconds(5).toMillis();
    private static final long OBJECTIVE_STALL_WARNING_MS = Duration.ofSeconds(30).toMillis();

    private AgentVictoriaLiveValidationRunner() {
    }

    public static void main(String[] args) throws Exception {
        String agentName = argument(args, 0, "KiwiAgent");
        String career = argument(args, 1, "magician");
        String checkpointText = argument(args, 2, "checkpoint3-hunt");
        VictoriaFirstJobMvpTestService.Checkpoint checkpoint =
                VictoriaFirstJobMvpTestService.resolveCheckpoint(checkpointText);

        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        Server.getInstance().init();

        AgentResolvedCharacter resolved = AgentPersistenceGatewayRuntime.persistence()
                .findCharacterByName(agentName);
        if (resolved == null) {
            throw new IllegalArgumentException("No character named '" + agentName + "' exists");
        }

        AgentPopulationAdminService population = AgentPopulationRuntime.admin();
        population.add(agentName);
        population.setMultiplier(1.0d);
        population.setEnabled(true);
        AgentRuntimeEntry entry = awaitLiveAgent(population, resolved.id());

        AgentMailboxRuntime.dispatch(entry, ignored -> {
            try {
                VictoriaFirstJobMvpTestService.resetAndStart(
                        entry, career, "lv10", checkpoint, System.currentTimeMillis());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return null;
        }).get(60, TimeUnit.SECONDS);

        log.info("VICTORIA_LIVE_STARTED agent={} characterId={} career={} checkpoint={}",
                agentName, resolved.id(), career, checkpointText);
        monitor(entry, population);
    }

    private static AgentRuntimeEntry awaitLiveAgent(
            AgentPopulationAdminService population, int characterId) throws Exception {
        long deadline = System.currentTimeMillis() + SPAWN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            population.sweep();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
            if (entry != null) {
                return entry;
            }
            Thread.sleep(1_000L);
        }
        throw new IllegalStateException("Agent population did not load character " + characterId);
    }

    private static void monitor(
            AgentRuntimeEntry entry, AgentPopulationAdminService population) throws Exception {
        long startedAtMs = System.currentTimeMillis();
        long lastObjectiveProgressAtMs = startedAtMs;
        long lastStallWarningAtMs = 0L;
        String previousObjectiveFingerprint = "";
        boolean forestHuntObservedComplete = false;

        while (true) {
            long nowMs = System.currentTimeMillis();
            Character agent = entry.bot();
            if (agent == null) {
                throw new IllegalStateException("Live validation Agent disconnected");
            }

            Sample sample = sample(entry, agent, nowMs);
            String objectiveFingerprint = sample.objectiveFingerprint();
            if (!objectiveFingerprint.equals(previousObjectiveFingerprint)) {
                previousObjectiveFingerprint = objectiveFingerprint;
                lastObjectiveProgressAtMs = nowMs;
            }

            log.info("VICTORIA_LIVE_SAMPLE {}", sample.summary(startedAtMs));
            if (!forestHuntObservedComplete && sample.forestHuntComplete()) {
                forestHuntObservedComplete = true;
                log.info("VICTORIA_FOREST_HUNT_COMPLETE elapsedMs={} orange={} pigs={} ribbons={}",
                        nowMs - startedAtMs, sample.orangeKills(), sample.pigKills(), sample.pigRibbons());
            }
            if (sample.nautilusPackComplete()) {
                population.setEnabled(false);
                log.info("VICTORIA_NAUTILUS_PACK_COMPLETE elapsedMs={} level={} map={} plan={} stage={}",
                        nowMs - startedAtMs, agent.getLevel(), agent.getMapId(),
                        sample.planStatus(), sample.stage());
                return;
            }
            if (sample.planStatus() == AgentPlanExecutionStatus.BLOCKED
                    || sample.planStatus() == AgentPlanExecutionStatus.FAILED
                    || sample.stage() == AgentCareerProgressionState.Stage.BLOCKED) {
                throw new IllegalStateException("Victoria validation blocked: " + sample.summary(startedAtMs));
            }

            long noObjectiveProgressMs = nowMs - lastObjectiveProgressAtMs;
            if ((sample.navigationStuckMs() >= OBJECTIVE_STALL_WARNING_MS
                    || noObjectiveProgressMs >= OBJECTIVE_STALL_WARNING_MS)
                    && nowMs - lastStallWarningAtMs >= OBJECTIVE_STALL_WARNING_MS) {
                lastStallWarningAtMs = nowMs;
                log.warn("VICTORIA_LIVE_STALL noObjectiveProgressMs={} {}",
                        noObjectiveProgressMs, sample.summary(startedAtMs));
            }
            Thread.sleep(SAMPLE_INTERVAL_MS);
        }
    }

    private static Sample sample(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentNavigationTraceSnapshot navigation = AgentNavigationTraceRuntime.snapshot(entry, nowMs);
        AgentCombatTargetTraceSnapshot combat = AgentCombatTargetTraceRuntime.snapshot(entry, nowMs);
        AgentCareerProgressionState progression = entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        Point position = agent.getPosition();
        return new Sample(
                nowMs,
                agent.getMapId(),
                position == null ? new Point() : new Point(position),
                progression.stage(),
                progression.questPackIndex(),
                AgentUniversalPlanRuntime.status(entry),
                questProgress(agent, ORANGE_QUEST_ID, ORANGE_MOB_ID),
                questProgress(agent, PIG_QUEST_ID, PIG_MOB_ID),
                agent.countItem(PIG_RIBBON_ITEM_ID),
                questProgress(agent, SLIME_QUEST_ID, SLIME_MOB_ID),
                questStatus(agent, ORANGE_QUEST_ID),
                questStatus(agent, PIG_QUEST_ID),
                questStatus(agent, RIBBON_QUEST_ID),
                questStatus(agent, SLIME_QUEST_ID),
                navigation,
                combat);
    }

    private static int questProgress(Character agent, int questId, int targetId) {
        String value = agent.getQuest(questId).getProgress(targetId);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int questStatus(Character agent, int questId) {
        return agent.getQuestStatus(questId);
    }

    private static String argument(String[] args, int index, String fallback) {
        return args != null && args.length > index && !args[index].isBlank()
                ? args[index].trim() : fallback;
    }

    record Sample(long sampledAtMs,
                  int mapId,
                  Point position,
                  AgentCareerProgressionState.Stage stage,
                  int questPackIndex,
                  AgentPlanExecutionStatus planStatus,
                  int orangeKills,
                  int pigKills,
                  int pigRibbons,
                  int slimeKills,
                  int orangeQuestStatus,
                  int pigQuestStatus,
                  int ribbonQuestStatus,
                  int slimeQuestStatus,
                  AgentNavigationTraceSnapshot navigation,
                  AgentCombatTargetTraceSnapshot combat) {
        private static final int COMPLETED = QuestStatus.Status.COMPLETED.getId();

        Sample {
            position = position == null ? new Point() : new Point(position);
        }

        @Override
        public Point position() {
            return new Point(position);
        }

        boolean forestHuntComplete() {
            return (orangeKills >= 30 || orangeQuestStatus == COMPLETED)
                    && (pigKills >= 30 || pigQuestStatus == COMPLETED)
                    && (pigRibbons >= 20 || ribbonQuestStatus == COMPLETED);
        }

        boolean nautilusPackComplete() {
            return List.of(orangeQuestStatus, pigQuestStatus, ribbonQuestStatus, slimeQuestStatus)
                    .stream().allMatch(status -> status == COMPLETED);
        }

        int navigationStuckMs() {
            return navigation == null ? 0 : navigation.stuckMs();
        }

        String objectiveFingerprint() {
            return mapId + ":" + stage + ":" + questPackIndex + ":" + orangeKills + ":"
                    + pigKills + ":" + pigRibbons + ":" + slimeKills + ":"
                    + orangeQuestStatus + ":" + pigQuestStatus + ":"
                    + ribbonQuestStatus + ":" + slimeQuestStatus;
        }

        String summary(long startedAtMs) {
            String nav = navigation == null
                    ? "nav=missing"
                    : "nav=" + navigation.currentRegionId() + "->" + navigation.targetRegionId()
                    + "/" + navigation.decision() + "/" + navigation.verticalStage()
                    + "/stuck=" + navigation.stuckMs()
                    + "/path=" + compactPath(navigation.path());
            String target = combat == null || !combat.hasTarget()
                    ? "target=none"
                    : "target=" + combat.targetMobId() + "@(" + combat.targetPosition().x()
                    + "," + combat.targetPosition().y() + ")/" + combat.reasonCode()
                    + "/" + combat.action();
            return "elapsedMs=" + (sampledAtMs - startedAtMs)
                    + " map=" + mapId + " pos=(" + position.x + "," + position.y + ")"
                    + " stage=" + stage + " packIndex=" + questPackIndex + " plan=" + planStatus
                    + " orange=" + orangeKills + " pigs=" + pigKills
                    + " ribbons=" + pigRibbons + " slimes=" + slimeKills
                    + " questStatus=" + orangeQuestStatus + "," + pigQuestStatus + ","
                    + ribbonQuestStatus + "," + slimeQuestStatus + " " + nav + " " + target;
        }

        private static String compactPath(List<AgentNavigationTraceSnapshot.Edge> path) {
            if (path == null || path.isEmpty()) {
                return "[]";
            }
            StringBuilder result = new StringBuilder("[").append(path.getFirst().fromRegionId());
            for (AgentNavigationTraceSnapshot.Edge edge : path) {
                result.append('>').append(edge.toRegionId());
            }
            return result.append(']').toString();
        }
    }
}
