package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.capabilities.inventory.demand.AgentQuestItemDemandRuntime;
import server.agents.capabilities.looting.AgentPreExitLootRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        if (!appliesToBundle(step, state.bundle())) {
            advance(state, nowMs);
            return Result.RUNNING;
        }
        announce(agent, state, packId, step.intention());
        return switch (step.type()) {
            case "TAXI" -> taxi(entry, agent, state, step, nowMs, gateway);
            case "QUEST" -> quest(entry, agent, state, step, nowMs, gateway);
            case "HUNT" -> hunt(entry, agent, state, packId, pack, step, nowMs, gateway);
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

    private static boolean appliesToBundle(
            AgentVictoriaSharedQuestPackCatalog.Step step,
            AgentCareerBuildBundle bundle) {
        return step.bundleIds().isEmpty()
                || (bundle != null && step.bundleIds().contains(bundle.bundleId()));
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
        gateway.runNpcScript(agent, town.taxiNpcId(),
                AgentTaxiDialogueSequence.regularTownCab(selection));
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
                               String packId,
                               AgentVictoriaSharedQuestPackCatalog.Pack pack,
                               AgentVictoriaSharedQuestPackCatalog.Step step,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        String selectionId = packId + ":" + state.questPackIndex();
        if (conditionsMet(agent, step, gateway)) {
            if (AgentPreExitLootRuntime.drain(entry, agent, nowMs)) {
                return Result.RUNNING;
            }
            gateway.stop(entry);
            AgentPreExitLootRuntime.clear(entry);
            AgentAdaptiveQuestHuntSelector.defaultSelector()
                    .clearCombinedSelection(agent.getId(), selectionId);
            advance(state, nowMs);
            return Result.RUNNING;
        }

        List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> objectives =
                unresolvedObjectives(agent, state, pack, step, gateway);
        AgentAdaptiveQuestHuntSelector.Selection selection =
                AgentAdaptiveQuestHuntSelector.defaultSelector()
                        .selectCombined(entry, agent, selectionId, step.mapId(),
                                step.preferredMobIds(), objectives, nowMs)
                        .orElse(null);
        int huntMapId = selection == null ? step.mapId() : selection.map().mapId();
        if (agent.getMapId() != huntMapId) {
            if (AgentVictoriaRouteRuntime.travel(entry, agent, huntMapId, gateway)) {
                return Result.RUNNING;
            }
        }
        Set<Integer> preferred = selection == null
                ? new HashSet<>(step.preferredMobIds())
                : new HashSet<>(selection.map().targetMobIds());
        Set<Integer> incidental = huntMapId == step.mapId()
                ? new HashSet<>(step.incidentalMobIds()) : Set.of();
        gateway.grind(entry, preferred, incidental);
        return Result.RUNNING;
    }

    private static List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference>
    unresolvedObjectives(
            Character agent,
            AgentCareerProgressionState state,
            AgentVictoriaSharedQuestPackCatalog.Pack pack,
            AgentVictoriaSharedQuestPackCatalog.Step step,
            PrimitiveCapabilityGateway gateway) {
        Set<Integer> activeQuestIds = pack.steps().stream()
                .limit(state.questPackIndex() + 1L)
                .filter(candidate -> "QUEST".equals(candidate.type())
                        && !candidate.complete() && candidate.questId() > 0)
                .map(AgentVictoriaSharedQuestPackCatalog.Step::questId)
                .filter(questId -> gateway.questStatus(agent, questId)
                        == QuestStatus.Status.STARTED.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> result =
                new LinkedHashMap<>();
        AgentVictoriaQuestHuntIndexRepository repository =
                AgentVictoriaQuestHuntIndexRepository.defaultRepository();
        for (AgentVictoriaSharedQuestPackCatalog.Condition condition : step.conditions()) {
            if (conditionMet(agent, condition, gateway)) {
                continue;
            }
            Set<Integer> questIds = condition.questId() > 0
                    ? Set.of(condition.questId()) : activeQuestIds;
            for (AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference
                    : repository.findObjectivesForTarget(questIds, condition.targetId())) {
                boolean expectedType = "QUEST_KILL".equals(condition.type())
                        ? reference.objective().type().contains("kill")
                        : reference.objective().type().contains("collect");
                if (expectedType) {
                    result.putIfAbsent(reference.questId() + ":"
                            + reference.objective().objectiveId(), reference);
                }
            }
        }
        return List.copyOf(result.values());
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
            if (inside && AgentPreExitLootRuntime.drain(entry, agent, nowMs)) {
                return Result.RUNNING;
            }
            if (!inside) {
                gateway.stop(entry);
                AgentPreExitLootRuntime.clear(entry);
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
            if (!conditionMet(agent, condition, gateway)) {
                return false;
            }
        }
        return true;
    }

    private static boolean conditionMet(
            Character agent,
            AgentVictoriaSharedQuestPackCatalog.Condition condition,
            PrimitiveCapabilityGateway gateway) {
        int current = switch (condition.type()) {
            case "QUEST_KILL" -> gateway.questProgress(
                    agent, condition.questId(), condition.targetId());
            case "ITEM" -> gateway.itemCount(agent, condition.targetId());
            default -> throw new IllegalStateException(
                    "unsupported shared quest-pack condition " + condition.type());
        };
        return current >= condition.count();
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
        if (AgentShopStateRuntime.lastVisitRequestedItem(
                entry, step.itemId(), step.itemCount())) {
            // Quest-pack return scrolls are an optimization. A completed, timed-out, or
            // unaffordable visit falls back to ordinary route travel instead of
            // reopening the same shop forever.
            advance(state, nowMs);
            return Result.RUNNING;
        }
        if (!AgentShopService.requestVisitAtNpc(entry, agent, step.npcId(), 0,
                step.itemId(), step.itemCount())) {
            return Result.BLOCKED;
        }
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
