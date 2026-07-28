package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.capabilities.shop.AgentShopWorkflowPhase;
import server.agents.capabilities.inventory.demand.AgentQuestItemDemandRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Data-driven executor for reusable Victoria home and rotation quest packs. */
final class AgentVictoriaSharedQuestPackRuntime {
    enum Result {
        RUNNING,
        COMPLETE,
        BLOCKED
    }

    private static final int NPC_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentVictoriaSharedQuestPackRuntime.NPC_DISTANCE_PX");
    private static final long STEP_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaSharedQuestPackRuntime.STEP_DELAY_MS");

    private AgentVictoriaSharedQuestPackRuntime() {
    }

    static Result tick(AgentRuntimeEntry entry,
                       Character agent,
                       AgentCareerProgressionState state,
                       String packId,
                       long nowMs,
                       PrimitiveCapabilityGateway gateway) {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require(packId);
        refreshQuestItemReservations(entry, agent, state, pack, nowMs);
        if (state.questPackIndex() >= pack.steps().size()) {
            return Result.COMPLETE;
        }
        AgentVictoriaSharedQuestPackCatalog.Step step =
                pack.steps().get(state.questPackIndex());
        announce(agent, state, packId, step.intention());
        return switch (step.type()) {
            case "TAXI" -> taxi(entry, agent, state, step, nowMs, gateway);
            case "QUEST" -> quest(entry, agent, state, step, nowMs, gateway);
            case "HUNT" -> hunt(entry, agent, state, step, nowMs, gateway);
            case "TRAVEL" -> travel(entry, agent, state, step, nowMs, gateway);
            case "PORTAL" -> portal(entry, agent, state, step, nowMs, gateway);
            case "USE_SCROLL" -> useScroll(entry, agent, state, step, nowMs, gateway);
            case "OPTIONAL_SCROLL" -> optionalScroll(entry, agent, state, step, nowMs, gateway);
            case "SHOP_ITEM" -> shopItem(entry, agent, state, step, nowMs, gateway);
            case "LEVEL_GRIND" -> levelGrind(entry, agent, state, step, nowMs, gateway);
            case "MINI_DUNGEON_HUNT" ->
                    miniDungeonHunt(entry, agent, state, step, nowMs, gateway);
            default -> throw new IllegalStateException(
                    "unsupported shared quest-pack step type " + step.type());
        };
    }

    private static void refreshQuestItemReservations(
            AgentRuntimeEntry entry,
            Character agent,
            AgentCareerProgressionState state,
            AgentVictoriaSharedQuestPackCatalog.Pack pack,
            long nowMs) {
        Set<Integer> committedQuestIds = pack.steps().stream()
                .skip(state.questPackIndex())
                .flatMap(step -> java.util.stream.Stream.concat(
                        step.questId() > 0
                                ? java.util.stream.Stream.of(step.questId())
                                : java.util.stream.Stream.empty(),
                        step.conditions().stream()
                                .map(AgentVictoriaSharedQuestPackCatalog.Condition::questId)
                                .filter(questId -> questId > 0)))
                .collect(Collectors.toUnmodifiableSet());
        Set<Integer> plannedJobs = state.bundle() == null
                ? Set.of() : Set.of(state.bundle().firstJobId());
        AgentQuestItemDemandRuntime.refreshReservations(
                entry, agent, committedQuestIds, plannedJobs, nowMs);
    }

    private static Result taxi(AgentRuntimeEntry entry,
                               Character agent,
                               AgentCareerProgressionState state,
                               AgentVictoriaSharedQuestPackCatalog.Step step,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() == step.destinationMapId()) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        AgentVictoriaSharedQuestPackCatalog.Town town =
                AgentVictoriaSharedQuestPackCatalog.town(agent.getMapId());
        if (town == null) {
            if (AgentVictoriaRouteRuntime.travel(entry, agent, step.mapId(), gateway)) {
                return Result.RUNNING;
            }
            town = AgentVictoriaSharedQuestPackCatalog.town(agent.getMapId());
        }
        if (town == null) {
            return Result.BLOCKED;
        }
        int selection = town.selectionFor(step.destinationMapId());
        Point taxi = gateway.npcPosition(agent, town.taxiNpcId());
        if (selection < 0 || taxi == null) {
            return Result.BLOCKED;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(taxi) > (long) NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, taxi, true);
            return Result.RUNNING;
        }
        gateway.facePosition(agent, taxi);
        gateway.stop(entry);
        gateway.runNpcScript(agent, town.taxiNpcId(), 0, 1, selection, 0);
        return Result.RUNNING;
    }

    private static Result quest(AgentRuntimeEntry entry,
                                Character agent,
                                AgentCareerProgressionState state,
                                AgentVictoriaSharedQuestPackCatalog.Step step,
                                long nowMs,
                                PrimitiveCapabilityGateway gateway) {
        if (step.requiredLevel() > 0 && agent.getLevel() < step.requiredLevel()) {
            return Result.BLOCKED;
        }
        int expected = step.complete()
                ? QuestStatus.Status.COMPLETED.getId() : QuestStatus.Status.STARTED.getId();
        int status = gateway.questStatus(agent, step.questId());
        if (status == expected || (!step.complete()
                && status == QuestStatus.Status.COMPLETED.getId())) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (step.mapId() > 0
                && AgentVictoriaRouteRuntime.travel(entry, agent, step.mapId(), gateway)) {
            return Result.RUNNING;
        }
        Point npc = gateway.npcPosition(agent, step.npcId());
        if (npc == null) {
            return Result.BLOCKED;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(npc) > (long) NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, npc, true);
            return Result.RUNNING;
        }
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        boolean changed = step.complete()
                ? gateway.canCompleteQuest(agent, step.questId(), step.npcId())
                && gateway.completeQuest(agent, step.questId(), step.npcId())
                : gateway.canStartQuest(agent, step.questId(), step.npcId())
                && gateway.startQuest(agent, step.questId(), step.npcId());
        if (changed || gateway.questStatus(agent, step.questId()) == expected) {
            advance(state, nowMs);
        }
        return Result.RUNNING;
    }

    private static Result hunt(AgentRuntimeEntry entry,
                               Character agent,
                               AgentCareerProgressionState state,
                               AgentVictoriaSharedQuestPackCatalog.Step step,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        if (conditionsMet(agent, step, gateway)) {
            gateway.stop(entry);
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (!inMap(step, agent.getMapId())) {
            if (AgentVictoriaRouteRuntime.travel(entry, agent, step.mapId(), gateway)) {
                return Result.RUNNING;
            }
        }
        Set<Integer> preferred = new HashSet<>(step.preferredMobIds());
        Set<Integer> incidental = new HashSet<>(step.incidentalMobIds());
        gateway.grind(entry, preferred, incidental);
        return Result.RUNNING;
    }

    private static Result miniDungeonHunt(
            AgentRuntimeEntry entry,
            Character agent,
            AgentCareerProgressionState state,
            AgentVictoriaSharedQuestPackCatalog.Step step,
            long nowMs,
            PrimitiveCapabilityGateway gateway) {
        boolean inside = inMap(step, agent.getMapId());
        if (conditionsMet(agent, step, gateway)) {
            if (!inside) {
                gateway.stop(entry);
                advance(state, nowMs);
                return Result.RUNNING;
            }
            return enterPortal(entry, agent, step.exitPortalId(), gateway);
        }
        if (inside) {
            gateway.grind(entry, Set.copyOf(step.preferredMobIds()),
                    Set.copyOf(step.incidentalMobIds()));
            return Result.RUNNING;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, step.destinationMapId(), gateway)) {
            return Result.RUNNING;
        }
        return enterPortal(entry, agent, step.portalId(), gateway);
    }

    private static Result enterPortal(AgentRuntimeEntry entry,
                                      Character agent,
                                      int portalId,
                                      PrimitiveCapabilityGateway gateway) {
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) {
            return Result.BLOCKED;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(portal) > (long) NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, portal, true);
            return Result.RUNNING;
        }
        gateway.stop(entry);
        gateway.enterPortal(agent, portalId);
        return Result.RUNNING;
    }

    private static boolean conditionsMet(Character agent,
                                         AgentVictoriaSharedQuestPackCatalog.Step step,
                                         PrimitiveCapabilityGateway gateway) {
        for (AgentVictoriaSharedQuestPackCatalog.Condition condition : step.conditions()) {
            int current = switch (condition.type()) {
                case "QUEST_KILL" -> gateway.questProgress(
                        agent, condition.questId(), condition.targetId());
                case "ITEM" -> gateway.itemCount(agent, condition.targetId());
                default -> throw new IllegalStateException(
                        "unsupported shared quest-pack condition " + condition.type());
            };
            if (current < condition.count()) {
                return false;
            }
        }
        return true;
    }

    private static Result travel(AgentRuntimeEntry entry,
                                 Character agent,
                                 AgentCareerProgressionState state,
                                 AgentVictoriaSharedQuestPackCatalog.Step step,
                                 long nowMs,
                                 PrimitiveCapabilityGateway gateway) {
        if (!AgentVictoriaRouteRuntime.travel(entry, agent, step.destinationMapId(), gateway)) {
            advance(state, nowMs);
        }
        return Result.RUNNING;
    }

    private static Result portal(AgentRuntimeEntry entry,
                                 Character agent,
                                 AgentCareerProgressionState state,
                                 AgentVictoriaSharedQuestPackCatalog.Step step,
                                 long nowMs,
                                 PrimitiveCapabilityGateway gateway) {
        boolean inDestination = inMap(step, agent.getMapId());
        if (inDestination) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        Point portal = gateway.portalPosition(agent, step.portalId());
        if (portal == null) {
            return Result.BLOCKED;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(portal) > (long) NPC_DISTANCE_PX * NPC_DISTANCE_PX) {
            gateway.navigate(entry, portal, true);
            return Result.RUNNING;
        }
        gateway.stop(entry);
        gateway.enterPortal(agent, step.portalId());
        return Result.RUNNING;
    }

    private static Result useScroll(AgentRuntimeEntry entry,
                                    Character agent,
                                    AgentCareerProgressionState state,
                                    AgentVictoriaSharedQuestPackCatalog.Step step,
                                    long nowMs,
                                    PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() == step.destinationMapId()) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (gateway.itemCount(agent, step.itemId()) > 0 && gateway.useItem(agent, step.itemId())) {
            return Result.RUNNING;
        }
        if (!AgentVictoriaRouteRuntime.travel(entry, agent, step.destinationMapId(), gateway)) {
            advance(state, nowMs);
        }
        return Result.RUNNING;
    }

    private static Result optionalScroll(AgentRuntimeEntry entry,
                                         Character agent,
                                         AgentCareerProgressionState state,
                                         AgentVictoriaSharedQuestPackCatalog.Step step,
                                         long nowMs,
                                         PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() == step.destinationMapId()) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (gateway.itemCount(agent, step.itemId()) > 0) {
            gateway.useItem(agent, step.itemId());
            return Result.RUNNING;
        }
        advance(state, nowMs);
        return Result.RUNNING;
    }

    private static Result shopItem(AgentRuntimeEntry entry,
                                   Character agent,
                                   AgentCareerProgressionState state,
                                   AgentVictoriaSharedQuestPackCatalog.Step step,
                                   long nowMs,
                                   PrimitiveCapabilityGateway gateway) {
        if (gateway.itemCount(agent, step.itemId()) >= step.itemCount()) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, step.mapId(), gateway)) {
            return Result.RUNNING;
        }
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            return Result.RUNNING;
        }
        AgentShopWorkflowPhase phase = AgentShopStateRuntime.workflow(entry).phase();
        if (phase == AgentShopWorkflowPhase.BLOCKED || phase == AgentShopWorkflowPhase.CANCELLED) {
            return Result.BLOCKED;
        }
        AgentShopService.requestVisitAtNpc(entry, agent, step.npcId(), 0,
                step.itemId(), step.itemCount());
        return Result.RUNNING;
    }

    private static Result levelGrind(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentCareerProgressionState state,
                                     AgentVictoriaSharedQuestPackCatalog.Step step,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        if (agent.getLevel() >= step.requiredLevel()) {
            gateway.stop(entry);
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (AgentVictoriaRouteRuntime.travel(entry, agent, step.mapId(), gateway)) {
            return Result.RUNNING;
        }
        gateway.grind(entry, Set.copyOf(step.preferredMobIds()),
                Set.copyOf(step.incidentalMobIds()));
        return Result.RUNNING;
    }

    private static boolean inMap(AgentVictoriaSharedQuestPackCatalog.Step step, int mapId) {
        if (step.instanceMapIdMin() > 0) {
            return mapId >= step.instanceMapIdMin() && mapId <= step.instanceMapIdMax();
        }
        return mapId == step.mapId();
    }

    private static void advance(AgentCareerProgressionState state, long nowMs) {
        state.questPackIndex(state.questPackIndex() + 1);
        state.stage(state.stage(), nowMs + STEP_DELAY_MS);
    }

    private static void announce(Character agent,
                                 AgentCareerProgressionState state,
                                 String packId,
                                 String intention) {
        VictoriaFirstJobNarrator.announceObjective(agent, state,
                state.stage() + ":" + packId + ":" + state.questPackIndex(), intention);
    }
}
