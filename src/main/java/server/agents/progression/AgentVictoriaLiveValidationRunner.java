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
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.movement.AgentMovementPhysicsStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

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
    private static final int BRUCE_QUEST_ID = 2_088;
    private static final int RINA_QUEST_ID = 28_267;
    private static final int CAMILA_QUEST_ID = 28_268;
    private static final int JAY_QUEST_ID = 28_269;
    private static final int CAMILA_PIG_MOB_ID = 1_210_100;
    private static final int JAY_RIBBON_PIG_MOB_ID = 1_210_101;
    private static final int ORANGE_MOB_ID = 1_210_102;
    private static final int SLIME_MOB_ID = 210_100;
    private static final int PIG_MOB_ID = 1_210_100;
    private static final int PIG_RIBBON_ITEM_ID = 4_000_002;
    private static final int ORANGE_MUSHROOM_CAP_ITEM_ID = 4_000_001;
    private static final int MUSHROOM_SPORE_ITEM_ID = 4_000_011;
    private static final int GREEN_MUSHROOM_CAP_ITEM_ID = 4_000_012;
    private static final long SAMPLE_INTERVAL_MS = Duration.ofSeconds(5).toMillis();
    private static final long OBJECTIVE_STALL_WARNING_MS = Duration.ofSeconds(30).toMillis();

    private AgentVictoriaLiveValidationRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("agents.victoria.liveValidation.enabled")) {
            throw new IllegalStateException(
                    "Set -Dagents.victoria.liveValidation.enabled=true for this developer runner");
        }
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

        Character loadedAgent = CosmicAgentOfflineLoader.loadOfflineAgent(
                resolved.id(), 0, 1, null, null);
        AgentRuntimeEntry entry = AgentInteractionRuntime.registerSelfDirectedAgent(loadedAgent);

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
        monitor(entry, validationTarget(career));
    }

    private static ValidationTarget validationTarget(String career) {
        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resolveBundle(career);
        String homePackId = AgentVictoriaLevel15CatalogRepository.defaultRepository()
                .careerFor(bundle).catchUpPlan().homePackId();
        return "henesys-pre15".equals(homePackId)
                ? ValidationTarget.HENESYS_HOME_PACK : ValidationTarget.NAUTILUS_ROTATION_PACK;
    }

    private static void monitor(AgentRuntimeEntry entry, ValidationTarget target) throws Exception {
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
            if (sample.validationComplete(target)) {
                log.info("VICTORIA_{}_COMPLETE elapsedMs={} level={} map={} plan={} stage={}",
                        target.logLabel(),
                        nowMs - startedAtMs, agent.getLevel(), agent.getMapId(),
                        sample.planStatus(), sample.stage());
                return;
            }
            if (target == ValidationTarget.HENESYS_HOME_PACK
                    && sample.stage() != AgentCareerProgressionState.Stage.HOME_QUEST_PACK) {
                throw new IllegalStateException(
                        "Henesys validation advanced before all four quests completed: "
                                + sample.summary(startedAtMs));
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
                agent.countItem(ORANGE_MUSHROOM_CAP_ITEM_ID),
                agent.countItem(MUSHROOM_SPORE_ITEM_ID),
                agent.countItem(GREEN_MUSHROOM_CAP_ITEM_ID),
                questStatus(agent, BRUCE_QUEST_ID),
                questStatus(agent, RINA_QUEST_ID),
                questStatus(agent, CAMILA_QUEST_ID),
                questStatus(agent, JAY_QUEST_ID),
                questProgress(agent, CAMILA_QUEST_ID, CAMILA_PIG_MOB_ID),
                questProgress(agent, JAY_QUEST_ID, JAY_RIBBON_PIG_MOB_ID),
                navigation,
                combat,
                movementDiagnostics(entry));
    }

    private static String movementDiagnostics(AgentRuntimeEntry entry) {
        Object active = AgentNavigationDebugStateRuntime.activeNavigationEdge(entry);
        String edge = active instanceof AgentNavigationGraph.Edge navEdge
                ? navEdge.type + ":" + navEdge.fromRegionId + ">" + navEdge.toRegionId
                + ":step=" + navEdge.launchStepX
                : "none";
        Point waypoint = AgentNavigationDebugStateRuntime.navTargetPosition(entry);
        Point goal = AgentMoveTargetStateRuntime.moveTarget(entry);
        return "edge=" + edge
                + ",waypoint=" + (waypoint == null ? "none" : waypoint.x + ":" + waypoint.y)
                + ",goal=" + (goal == null ? "none" : goal.x + ":" + goal.y)
                + ",air=" + AgentMovementStateRuntime.inAir(entry)
                + ",dir=" + AgentMovementStateRuntime.moveDirection(entry)
                + ",physX=" + Math.round(AgentMovementPhysicsStateRuntime.physicsX(entry) * 10.0) / 10.0
                + ",hSpeed=" + Math.round(AgentMovementPhysicsStateRuntime.horizontalSpeed(entry) * 100.0) / 100.0;
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
                  int orangeMushroomCaps,
                  int mushroomSpores,
                  int greenMushroomCaps,
                  int bruceQuestStatus,
                  int rinaQuestStatus,
                  int camilaQuestStatus,
                  int jayQuestStatus,
                  int camilaPigKills,
                  int jayRibbonPigKills,
                  AgentNavigationTraceSnapshot navigation,
                  AgentCombatTargetTraceSnapshot combat,
                  String movementDiagnostics) {
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

        boolean henesysPackComplete() {
            return List.of(bruceQuestStatus, rinaQuestStatus, camilaQuestStatus, jayQuestStatus)
                    .stream().allMatch(status -> status == COMPLETED);
        }

        boolean validationComplete(ValidationTarget target) {
            return target == ValidationTarget.HENESYS_HOME_PACK
                    ? henesysPackComplete() : nautilusPackComplete();
        }

        int navigationStuckMs() {
            return navigation == null ? 0 : navigation.stuckMs();
        }

        String objectiveFingerprint() {
            return mapId + ":" + stage + ":" + questPackIndex + ":" + orangeKills + ":"
                    + pigKills + ":" + pigRibbons + ":" + slimeKills + ":"
                    + orangeQuestStatus + ":" + pigQuestStatus + ":"
                    + ribbonQuestStatus + ":" + slimeQuestStatus + ":"
                    + orangeMushroomCaps + ":" + mushroomSpores + ":" + greenMushroomCaps + ":"
                    + bruceQuestStatus + ":" + rinaQuestStatus + ":"
                    + camilaQuestStatus + ":" + jayQuestStatus + ":"
                    + camilaPigKills + ":" + jayRibbonPigKills;
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
                    + ribbonQuestStatus + "," + slimeQuestStatus
                    + " henesysItems=" + orangeMushroomCaps + "," + mushroomSpores + ","
                    + greenMushroomCaps + " henesysStatus=" + bruceQuestStatus + ","
                    + rinaQuestStatus + "," + camilaQuestStatus + "," + jayQuestStatus
                    + " henesysKills=" + camilaPigKills + "," + jayRibbonPigKills
                    + " " + nav + " " + target
                    + " movement={" + movementDiagnostics + "}";
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

    private enum ValidationTarget {
        HENESYS_HOME_PACK("HENESYS_PACK"),
        NAUTILUS_ROTATION_PACK("NAUTILUS_PACK");

        private final String logLabel;

        ValidationTarget(String logLabel) {
            this.logLabel = logLabel;
        }

        String logLabel() {
            return logLabel;
        }
    }
}
