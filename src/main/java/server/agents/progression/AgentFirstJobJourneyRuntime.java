package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.capabilities.shop.AgentShopWorkflowPhase;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.objectives.AgentObjectiveAttachment;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

/** Real-script Southperry -> Lith -> taxi -> instructor -> first-job journey. */
public final class AgentFirstJobJourneyRuntime {
    public static final String OBJECTIVE_TYPE = "progression.first-job-level15";
    private static final int SOUTHPERRY_MAP_ID = 2_000_000;
    private static final int SHANKS_NPC_ID = 22_000;
    private static final int LITH_TAXI_NPC_ID = 1_002_000;
    private static final int INTERACTION_DISTANCE_PX = 100;
    private static final long INTERACTION_DELAY_MS = 3_000L;

    private AgentFirstJobJourneyRuntime() {
    }

    public static AgentObjectiveAttachment reattach(AgentRuntimeEntry entry,
                                                    Character agent,
                                                    AgentObjectiveDefinition objective,
                                                    long nowMs) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        if (state.bundle() == null) {
            throw new IllegalStateException("career build bundle was not restored before objective attachment");
        }
        if (state.stage() == AgentCareerProgressionState.Stage.COMPLETE) {
            AgentObjectiveKernel.transition(entry, objective.objectiveId(), AgentObjectiveStatus.SUCCEEDED,
                    "career checkpoint is already complete", nowMs);
            return AgentObjectiveAttachment.TERMINAL;
        }
        if (state.stage() == AgentCareerProgressionState.Stage.BLOCKED) {
            AgentObjectiveKernel.transition(entry, objective.objectiveId(), AgentObjectiveStatus.BLOCKED,
                    state.blockReason(), nowMs);
            return AgentObjectiveAttachment.TERMINAL;
        }
        return AgentObjectiveAttachment.ALREADY_ATTACHED;
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        try {
            return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
        } finally {
            AgentCareerProgressionCheckpointRuntime.persistIfDirty(entry, nowMs);
            AgentVictoriaProgressionDiagnostics.captureIfLevelChanged(entry, agent, nowMs);
        }
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = state.bundle();
        if (bundle == null || state.stage() == AgentCareerProgressionState.Stage.COMPLETE
                || state.stage() == AgentCareerProgressionState.Stage.BLOCKED) {
            return false;
        }
        if (agent.getJob().getId() == 0
                && state.stage() == AgentCareerProgressionState.Stage.WAITING_FOR_MAPLE_ISLAND
                && !entry.amherstPlanExecutionState().completed()
                && agent.getMapId() < 100_000_000) {
            return false;
        }
        reconcile(state, agent, bundle, nowMs);
        if (state.stage() != AgentCareerProgressionState.Stage.WAITING_FOR_MAPLE_ISLAND) {
            AgentCareerObjectiveRuntime.ensureStarted(entry, bundle, nowMs);
        }
        if (!state.ready(nowMs)) {
            return true;
        }
        return switch (state.stage()) {
            case WAITING_FOR_MAPLE_ISLAND -> false;
            case TRAVEL_TO_LITH -> approachAndRun(entry, agent, SHANKS_NPC_ID,
                    () -> gateway.runNpcScript(agent, SHANKS_NPC_ID), gateway);
            case COMPLETE_BIGGS_AT_OLAF -> completeBiggsAtOlaf(entry, agent, gateway);
            case COMPLETE_OLAF_LESSON -> completeOlafLesson(entry, agent, state, nowMs, gateway);
            case START_CAREER_PATH -> startCareerPath(entry, agent, bundle, gateway);
            case TRAVEL_TO_PRE_JOB_GRIND -> travelToPreJobGrind(entry, agent, gateway);
            case GRIND_TO_JOB_LEVEL -> grindToJobLevel(entry, agent, state, nowMs, gateway);
            case RETURN_TO_LITH_FOR_TAXI -> returnToLith(entry, agent, state, nowMs, gateway);
            case TAKE_TAXI -> approachAndRun(entry, agent, LITH_TAXI_NPC_ID,
                    () -> gateway.runNpcScript(agent, LITH_TAXI_NPC_ID,
                            0, 1, taxiSelection(bundle), 0), gateway);
            case ENTER_INSTRUCTOR_ROOM -> enterInstructorRoom(entry, agent, bundle, gateway);
            case COMPLETE_CAREER_PATH -> completeCareerPath(entry, agent, bundle, gateway);
            case ADVANCE_FIRST_JOB -> approachAndRun(entry, agent, bundle.instructorNpcId(),
                    () -> advanceAtInstructor(entry, agent, bundle, nowMs, gateway), gateway);
            case TRAVEL_TO_INITIAL_SHOP -> travelToInitialShop(entry, agent, state, bundle, nowMs, gateway);
            case INITIAL_SHOPPING -> waitForInitialShopping(entry, state, nowMs);
            case RETURN_TO_INSTRUCTOR -> returnToInstructor(entry, agent, state, bundle, nowMs, gateway);
            case INSTRUCTOR_TRAINING ->
                    AgentInstructorTrainingRuntime.tick(entry, agent, nowMs, gateway);
            case HOME_QUEST_PACK, POST_HOME_DECISION, ROTATION_QUEST_PACK,
                    GRIND_TO_MILESTONE, FINAL_RETURN_TO_INSTRUCTOR ->
                    AgentLevel15CatchUpRuntime.tick(entry, agent, nowMs, gateway);
            case COMPLETE, BLOCKED -> false;
        };
    }

    private static void reconcile(AgentCareerProgressionState state,
                                  Character agent,
                                  AgentCareerBuildBundle bundle,
                                  long nowMs) {
        if ((state.stage() == AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING
                || state.stage() == AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE)
                && agent.getLevel() >= bundle.milestoneLevel()
                && agent.getJob().getId() == bundle.firstJobId()
                && state.trainingQuestIndex() >= bundle.instructorTrainingQuestIds().size()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.FINAL_RETURN_TO_INSTRUCTOR, nowMs);
            return;
        }
        if (agent.getJob().getId() == bundle.firstJobId()) {
            if (preAdvancementStage(state.stage())) {
                AgentCareerProgressionState.Stage next =
                        state.runMode() == AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP
                                ? AgentCareerProgressionState.Stage.TRAVEL_TO_INITIAL_SHOP
                                : AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING;
                state.stage(next, nowMs + INTERACTION_DELAY_MS);
            }
            return;
        }
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = handoff();
        AgentVictoriaLevel15Catalog.Career career = career(bundle);
        if (agent.getMapId() == SOUTHPERRY_MAP_ID
                && (state.stage() == AgentCareerProgressionState.Stage.WAITING_FOR_MAPLE_ISLAND
                || state.stage() == AgentCareerProgressionState.Stage.TRAVEL_TO_LITH)) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.TRAVEL_TO_LITH,
                    nowMs + INTERACTION_DELAY_MS);
            return;
        }
        if (agent.getQuestStatus(handoff.biggsQuestId()) != QuestStatus.Status.COMPLETED.getId()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF, nowMs);
            return;
        }
        if (agent.getQuestStatus(handoff.olafLessonQuestId()) != QuestStatus.Status.COMPLETED.getId()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.COMPLETE_OLAF_LESSON,
                    nowMs + INTERACTION_DELAY_MS);
            return;
        }
        int pathStatus = agent.getQuestStatus(career.olafPathQuestId());
        if (pathStatus == QuestStatus.Status.NOT_STARTED.getId()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.START_CAREER_PATH,
                    nowMs + INTERACTION_DELAY_MS);
            return;
        }
        if (agent.getLevel() < handoff.targetLevel()) {
            AgentCareerProgressionState.Stage next = agent.getMapId() == handoff.grindMapId()
                    ? AgentCareerProgressionState.Stage.GRIND_TO_JOB_LEVEL
                    : AgentCareerProgressionState.Stage.TRAVEL_TO_PRE_JOB_GRIND;
            transitionIfNeeded(state, next, nowMs);
            return;
        }
        if (agent.getMapId() == handoff.grindMapId()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.RETURN_TO_LITH_FOR_TAXI, nowMs);
            return;
        }
        if (agent.getMapId() == bundle.instructorMapId()) {
            AgentCareerProgressionState.Stage next = pathStatus == QuestStatus.Status.COMPLETED.getId()
                    ? AgentCareerProgressionState.Stage.ADVANCE_FIRST_JOB
                    : AgentCareerProgressionState.Stage.COMPLETE_CAREER_PATH;
            transitionIfNeeded(state, next, nowMs + INTERACTION_DELAY_MS);
        } else if (agent.getMapId() == destinationTownMap(bundle)) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.ENTER_INSTRUCTOR_ROOM, nowMs);
        } else if (agent.getMapId() == handoff.lithHarborMapId()) {
            transitionIfNeeded(state, AgentCareerProgressionState.Stage.TAKE_TAXI,
                    nowMs + INTERACTION_DELAY_MS);
        }
    }

    private static boolean preAdvancementStage(AgentCareerProgressionState.Stage stage) {
        return switch (stage) {
            case WAITING_FOR_MAPLE_ISLAND, TRAVEL_TO_LITH, COMPLETE_BIGGS_AT_OLAF,
                    COMPLETE_OLAF_LESSON, START_CAREER_PATH, TRAVEL_TO_PRE_JOB_GRIND,
                    GRIND_TO_JOB_LEVEL, RETURN_TO_LITH_FOR_TAXI, TAKE_TAXI,
                    ENTER_INSTRUCTOR_ROOM, COMPLETE_CAREER_PATH, ADVANCE_FIRST_JOB -> true;
            default -> false;
        };
    }

    private static void transitionIfNeeded(AgentCareerProgressionState state,
                                           AgentCareerProgressionState.Stage stage,
                                           long nextActionAtMs) {
        if (state.stage() != stage) {
            state.stage(stage, nextActionAtMs);
        }
    }

    private static boolean enterInstructorRoom(AgentRuntimeEntry entry,
                                               Character agent,
                                               AgentCareerBuildBundle bundle,
                                               PrimitiveCapabilityGateway gateway) {
        return AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway);
    }

    private static boolean completeBiggsAtOlaf(AgentRuntimeEntry entry,
                                               Character agent,
                                               PrimitiveCapabilityGateway gateway) {
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = handoff();
        if (AgentVictoriaRouteRuntime.travel(entry, agent, handoff.lithHarborMapId(), gateway)) {
            return true;
        }
        interactQuestAtNpc(entry, agent, handoff.olafNpcId(), handoff.biggsQuestId(), true, gateway);
        return true;
    }

    private static boolean completeOlafLesson(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentCareerProgressionState state,
                                              long nowMs,
                                              PrimitiveCapabilityGateway gateway) {
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = handoff();
        if (AgentVictoriaRouteRuntime.travel(entry, agent, handoff.lithHarborMapId(), gateway)) {
            return true;
        }
        int status = gateway.questStatus(agent, handoff.olafLessonQuestId());
        boolean completed = status == QuestStatus.Status.STARTED.getId();
        if (interactQuestAtNpc(entry, agent, handoff.olafNpcId(), handoff.olafLessonQuestId(),
                completed, gateway) && !completed) {
            state.stage(AgentCareerProgressionState.Stage.COMPLETE_OLAF_LESSON,
                    nowMs + INTERACTION_DELAY_MS);
        }
        return true;
    }

    private static boolean startCareerPath(AgentRuntimeEntry entry,
                                           Character agent,
                                           AgentCareerBuildBundle bundle,
                                           PrimitiveCapabilityGateway gateway) {
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = handoff();
        if (AgentVictoriaRouteRuntime.travel(entry, agent, handoff.lithHarborMapId(), gateway)) {
            return true;
        }
        interactQuestAtNpc(entry, agent, handoff.olafNpcId(), career(bundle).olafPathQuestId(),
                false, gateway);
        return true;
    }

    private static boolean travelToPreJobGrind(AgentRuntimeEntry entry,
                                                Character agent,
                                                PrimitiveCapabilityGateway gateway) {
        return AgentVictoriaRouteRuntime.travel(entry, agent, handoff().grindMapId(), gateway);
    }

    private static boolean grindToJobLevel(AgentRuntimeEntry entry,
                                           Character agent,
                                           AgentCareerProgressionState state,
                                           long nowMs,
                                           PrimitiveCapabilityGateway gateway) {
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = handoff();
        if (agent.getLevel() >= handoff.targetLevel()) {
            state.stage(AgentCareerProgressionState.Stage.RETURN_TO_LITH_FOR_TAXI, nowMs);
            gateway.stop(entry);
            return true;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, handoff.grindMapId(), gateway)) {
            return true;
        }
        gateway.grind(entry, Set.copyOf(handoff.grindMobIds()));
        return true;
    }

    private static boolean returnToLith(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentCareerProgressionState state,
                                        long nowMs,
                                        PrimitiveCapabilityGateway gateway) {
        if (AgentVictoriaRouteRuntime.travel(entry, agent, handoff().lithHarborMapId(), gateway)) {
            return true;
        }
        state.stage(AgentCareerProgressionState.Stage.TAKE_TAXI,
                nowMs + INTERACTION_DELAY_MS);
        return true;
    }

    private static boolean completeCareerPath(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentCareerBuildBundle bundle,
                                              PrimitiveCapabilityGateway gateway) {
        if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
            return true;
        }
        interactQuestAtNpc(entry, agent, bundle.instructorNpcId(), career(bundle).olafPathQuestId(),
                true, gateway);
        return true;
    }

    private static boolean interactQuestAtNpc(AgentRuntimeEntry entry,
                                              Character agent,
                                              int npcId,
                                              int questId,
                                              boolean complete,
                                              PrimitiveCapabilityGateway gateway) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return false;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return false;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        return complete
                ? gateway.canCompleteQuest(agent, questId, npcId)
                && gateway.completeQuest(agent, questId, npcId)
                : gateway.canStartQuest(agent, questId, npcId)
                && gateway.startQuest(agent, questId, npcId);
    }

    private static boolean travelToInitialShop(AgentRuntimeEntry entry,
                                               Character agent,
                                               AgentCareerProgressionState state,
                                               AgentCareerBuildBundle bundle,
                                               long nowMs,
                                               PrimitiveCapabilityGateway gateway) {
        AgentCareerShopCatalog.ShopStop stop = AgentCareerShopCatalog.forBundle(bundle);
        if (AgentVictoriaRouteRuntime.travel(entry, agent, stop.mapId(), gateway)) {
            return true;
        }
        if (gateway.npcPosition(agent, stop.npcId()) == null) {
            block(entry, state, "configured potion-shop NPC " + stop.npcId() + " is missing", nowMs);
            return false;
        }
        if (!AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentShopService.onMapChange(entry, agent, AgentInventoryGatewayRuntime.inventory());
        }
        if (!AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentShopService.requestVisitAtNpc(entry, agent, stop.npcId());
        }
        if (!AgentShopStateRuntime.shopVisitPending(entry)) {
            block(entry, state, "configured potion shop could not start its planned visit", nowMs);
            return false;
        }
        state.stage(AgentCareerProgressionState.Stage.INITIAL_SHOPPING, nowMs);
        return true;
    }

    private static boolean waitForInitialShopping(AgentRuntimeEntry entry,
                                                  AgentCareerProgressionState state,
                                                  long nowMs) {
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            return true;
        }
        AgentShopWorkflowPhase phase = AgentShopStateRuntime.workflow(entry).phase();
        if (phase == AgentShopWorkflowPhase.COMPLETED) {
            state.stage(AgentCareerProgressionState.Stage.RETURN_TO_INSTRUCTOR,
                    nowMs + INTERACTION_DELAY_MS);
            return true;
        }
        if (phase == AgentShopWorkflowPhase.BLOCKED || phase == AgentShopWorkflowPhase.CANCELLED) {
            block(entry, state, "initial potion-shop visit ended in " + phase, nowMs);
            return false;
        }
        return true;
    }

    private static boolean returnToInstructor(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentCareerProgressionState state,
                                              AgentCareerBuildBundle bundle,
                                              long nowMs,
                                              PrimitiveCapabilityGateway gateway) {
        if (AgentVictoriaRouteRuntime.travel(entry, agent, bundle.instructorMapId(), gateway)) {
            return true;
        }
        state.stage(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING,
                nowMs + INTERACTION_DELAY_MS);
        return true;
    }

    private static void block(AgentRuntimeEntry entry,
                              AgentCareerProgressionState state,
                              String reason,
                              long nowMs) {
        state.block(reason);
        AgentCareerObjectiveRuntime.block(entry, reason, nowMs);
    }

    private static void advanceAtInstructor(AgentRuntimeEntry entry,
                                            Character agent,
                                            AgentCareerBuildBundle bundle,
                                            long nowMs,
                                            PrimitiveCapabilityGateway gateway) {
        Job oldJob = agent.getJob();
        if (!gateway.runNpcScript(agent, bundle.instructorNpcId())) {
            return;
        }
        Job newJob = agent.getJob();
        if (oldJob != newJob) {
            AgentBuildService.handleJobAdvance(entry, agent, oldJob, newJob);
            AgentEquipmentService.autoEquip(agent, null, null, true);
            int preferredWeaponItemId = career(bundle).preferredStarterWeaponByBundleId()
                    .get(bundle.bundleId());
            if (!AgentEquipmentService.equipPreferredWeapon(agent, preferredWeaponItemId)) {
                block(entry, entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY),
                        "could not equip preferred starter weapon " + preferredWeaponItemId
                                + " for " + bundle.bundleId(), nowMs);
            }
        }
    }

    private static boolean approachAndRun(AgentRuntimeEntry entry,
                                          Character agent,
                                          int npcId,
                                          Runnable interaction,
                                          PrimitiveCapabilityGateway gateway) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return true;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > INTERACTION_DISTANCE_PX * INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return true;
        }
        gateway.facePosition(agent, npc);
        interaction.run();
        return true;
    }

    private static int taxiSelection(AgentCareerBuildBundle bundle) {
        return career(bundle).taxiSelection();
    }

    private static int destinationTownMap(AgentCareerBuildBundle bundle) {
        return career(bundle).townMapId();
    }

    private static AgentVictoriaLevel15Catalog.Career career(AgentCareerBuildBundle bundle) {
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().careerFor(bundle);
    }

    private static AgentVictoriaLevel15Catalog.IslandHandoff handoff() {
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().islandHandoff();
    }
}
