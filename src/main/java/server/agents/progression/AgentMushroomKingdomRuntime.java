package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;

import java.awt.Point;
import java.util.Set;

/** Resumable Mushroom Kingdom quest executor built on generic Questing/Hunting capabilities. */
public final class AgentMushroomKingdomRuntime {
    private static final int INTERACTION_DISTANCE_PX = 180;
    private static final long INTERACTION_RETRY_MS = 650L;
    private static final long OBJECTIVE_TIMEOUT_MS = 45 * 60_000L;
    private static final Set<Integer> PEPE_KING_VARIANTS = Set.of(3300005, 3300006, 3300007);

    private AgentMushroomKingdomRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentMushroomKingdomState state = entry.capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        if (state.phase() != AgentMushroomKingdomState.Phase.ACTIVE) return false;
        if (gateway.questStatus(agent, AgentMushroomKingdomCatalog.FINAL_QUEST_ID)
                == QuestStatus.Status.COMPLETED.getId()) {
            gateway.stop(entry);
            state.complete("Mushroom Kingdom mainline and non-repeatable side quests complete");
            return true;
        }
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(agent.getJob().getId());
        if (gateway.questStatus(agent, entryQuest) != QuestStatus.Status.COMPLETED.getId()) {
            return entryQuest(entry, agent, entryQuest, state, gateway, nowMs);
        }

        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> gateway.questStatus(agent, candidate.questId())
                        != QuestStatus.Status.COMPLETED.getId())
                .findFirst().orElse(null);
        if (node == null) {
            gateway.stop(entry);
            state.complete("all catalogued Mushroom Kingdom quests complete");
            return true;
        }
        int metric = objectiveMetric(agent, node, gateway);
        state.observe(node.questId(), metric, gateway.mapId(agent), gateway.position(agent), nowMs);
        state.active(reason(node, metric));
        if (nowMs - state.progressAtMs() > OBJECTIVE_TIMEOUT_MS) {
            return block(entry, state, gateway, "quest " + node.questId()
                    + " made no quest, map, or physical progress for 45 minutes");
        }

        return switch (node.questId()) {
            case 2314 -> scriptedInvestigation(entry, agent, node, 106020300, 1,
                    state, gateway, nowMs);
            case 2322 -> scriptedInvestigation(entry, agent, node, 106020400, 3,
                    state, gateway, nowMs);
            case 2324 -> thornBarrier(entry, agent, state, gateway, nowMs);
            case 2330 -> pepeKings(entry, agent, state, gateway, nowMs);
            case 2332 -> enterPrimeMinister(entry, agent, state, gateway, nowMs);
            case 2335 -> secretRoom(entry, agent, state, gateway, nowMs);
            case 2331 -> royalSeal(entry, agent, state, gateway, nowMs);
            case 2336 -> truthAndReturn(entry, agent, state, gateway, nowMs);
            default -> ordinary(entry, agent, node, state, gateway, nowMs);
        };
    }

    public static void cancel(AgentRuntimeEntry entry, Character agent) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
    }

    private static boolean entryQuest(AgentRuntimeEntry entry, Character agent, int questId,
                                      AgentMushroomKingdomState state,
                                      PrimitiveCapabilityGateway gateway, long nowMs) {
        state.observe(questId, gateway.questStatus(agent, questId), gateway.mapId(agent),
                gateway.position(agent), nowMs);
        state.active("presenting Explorer recommendation at Mushroom Kingdom entrance");
        if (!travel(entry, agent, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID, state, gateway, nowMs)) {
            return true;
        }
        return interact(entry, agent, 1300005, state, gateway, nowMs,
                () -> gateway.completeQuest(agent, questId, 1300005));
    }

    private static boolean ordinary(AgentRuntimeEntry entry, Character agent,
                                    AgentMushroomKingdomCatalog.QuestNode node,
                                    AgentMushroomKingdomState state,
                                    PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, node.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!travel(entry, agent, node.startMapId(), state, gateway, nowMs)) return true;
            return interact(entry, agent, node.startNpcId(), state, gateway, nowMs,
                    () -> gateway.startQuest(agent, node.questId(), node.startNpcId()));
        }
        if (node.hunting() && objectiveMetric(agent, node, gateway) < node.requiredCount()) {
            if ((node.huntMapId() == 106020400 || node.huntMapId() == 106020401)
                    && gateway.mapId(agent) != 106020400
                    && gateway.questStatus(agent, 100202) == QuestStatus.Status.NOT_STARTED.getId()) {
                return killerSporeBarrier(entry, agent, state, gateway, nowMs);
            }
            if (!travel(entry, agent, node.huntMapId(), state, gateway, nowMs)) return true;
            if (node.itemId() > 0) gateway.lootNearby(agent, Set.of(node.itemId()));
            if (node.questId() == 2333) gateway.lootNearby(agent, Set.of(4001318));
            if (!node.mobIds().isEmpty()) {
                AgentQuestHuntingBridge.engage(entry, agent, gateway,
                        "mushroom-kingdom:" + node.questId(),
                        AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                        node.mobIds(), Set.of(), nowMs);
            }
            return true;
        }
        if (!travel(entry, agent, node.completeMapId(), state, gateway, nowMs)) return true;
        return interact(entry, agent, node.completeNpcId(), state, gateway, nowMs,
                () -> gateway.completeQuest(agent, node.questId(), node.completeNpcId()));
    }

    private static boolean killerSporeBarrier(AgentRuntimeEntry entry, Character agent,
                                               AgentMushroomKingdomState state,
                                               PrimitiveCapabilityGateway gateway, long nowMs) {
        if (!travel(entry, agent, 106020300, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 3, gateway)) return true;
        gateway.stop(entry);
        if (gateway.itemCount(agent, 2430014) > 0) gateway.useItem(agent, 2430014);
        if (gateway.questStatus(agent, 100202) == QuestStatus.Status.NOT_STARTED.getId()) {
            return capabilityFailure(entry, state, gateway,
                    "Killer Mushroom Spore did not open the first thorn barrier", nowMs);
        }
        state.capabilityProgress();
        gateway.enterPortal(agent, 3);
        return true;
    }

    private static boolean scriptedInvestigation(AgentRuntimeEntry entry, Character agent,
                                                 AgentMushroomKingdomCatalog.QuestNode node,
                                                 int mapId, int portalId,
                                                 AgentMushroomKingdomState state,
                                                 PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, node.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (gateway.questProgress(agent, node.questId(), node.questId()) < 1) {
            if (mapId == 106020400
                    && gateway.questStatus(agent, 100202) == QuestStatus.Status.NOT_STARTED.getId()) {
                return killerSporeBarrier(entry, agent, state, gateway, nowMs);
            }
            if (!travel(entry, agent, mapId, state, gateway, nowMs)) return true;
            if (!nearPortal(entry, agent, portalId, gateway)) return true;
            gateway.stop(entry);
            gateway.enterPortal(agent, portalId);
            return true;
        }
        if (!travel(entry, agent, node.completeMapId(), state, gateway, nowMs)) return true;
        return interact(entry, agent, node.completeNpcId(), state, gateway, nowMs,
                () -> gateway.completeQuest(agent, node.questId(), node.completeNpcId()));
    }

    private static boolean thornBarrier(AgentRuntimeEntry entry, Character agent,
                                        AgentMushroomKingdomState state,
                                        PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, 2324);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.require(2324);
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (!travel(entry, agent, 106020400, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 3, gateway)) return true;
        gateway.stop(entry);
        if (gateway.itemCount(agent, 2430015) > 0) gateway.useItem(agent, 2430015);
        if (gateway.questStatus(agent, 2324) != QuestStatus.Status.COMPLETED.getId()) {
            return capabilityFailure(entry, state, gateway,
                    "Thorn Remover did not clear the castle route", nowMs);
        }
        state.capabilityProgress();
        gateway.enterPortal(agent, 3);
        return true;
    }

    private static boolean pepeKings(AgentRuntimeEntry entry, Character agent,
                                     AgentMushroomKingdomState state,
                                     PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, 2330);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, AgentMushroomKingdomCatalog.require(2330), state, gateway, nowMs);
        }
        int progress = PEPE_KING_VARIANTS.stream()
                .mapToInt(mob -> Math.min(1, gateway.questProgress(agent, 2330, mob))).sum();
        if (progress >= 3) {
            if (gateway.mapId(agent) == 106021500) {
                if (!nearPortal(entry, agent, 1, gateway)) return true;
                gateway.enterPortal(agent, 1);
                return true;
            }
            return ordinary(entry, agent, AgentMushroomKingdomCatalog.require(2330), state, gateway, nowMs);
        }
        if (gateway.mapId(agent) == 106021500) {
            if (gateway.liveMonsterCount(agent, PEPE_KING_VARIANTS) > 0) {
                AgentQuestHuntingBridge.engage(entry, agent, gateway, "mushroom-kingdom:2330",
                        AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                        PEPE_KING_VARIANTS, Set.of(), nowMs);
                return true;
            }
            if (!nearPortal(entry, agent, 1, gateway)) return true;
            gateway.enterPortal(agent, 1);
            return true;
        }
        if (!travel(entry, agent, 106021400, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 2, gateway)) return true;
        gateway.stop(entry);
        if (!gateway.runPortalNpcScript(agent, 2, 1300013, 1)) {
            return capabilityFailure(entry, state, gateway,
                    "King Pepe portal dialogue did not start an instance", nowMs);
        }
        state.capabilityProgress();
        return true;
    }

    private static boolean enterPrimeMinister(AgentRuntimeEntry entry, Character agent,
                                              AgentMushroomKingdomState state,
                                              PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.questStatus(agent, 2332) == QuestStatus.Status.COMPLETED.getId()) return true;
        if (gateway.questStatus(agent, 2332) == QuestStatus.Status.NOT_STARTED.getId()
                && !gateway.startQuest(agent, 2332, 1300002)) {
            return capabilityFailure(entry, state, gateway,
                    "Princess rescue quest could not start after receiving the key", nowMs);
        }
        if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 2, gateway)) return true;
        gateway.stop(entry);
        if (!gateway.enterPortal(agent, 2)) {
            return capabilityFailure(entry, state, gateway,
                    "Prime Minister scripted portal did not start an instance", nowMs);
        }
        state.capabilityProgress();
        return true;
    }

    private static boolean secretRoom(AgentRuntimeEntry entry, Character agent,
                                      AgentMushroomKingdomState state,
                                      PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, 2335);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!ensurePrincessMap(entry, agent, state, gateway, nowMs)) return true;
            return interact(entry, agent, 1300002, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, 2335, 1300002));
        }
        if (!travel(entry, agent, 106021000, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 3, gateway)) return true;
        gateway.stop(entry);
        gateway.enterPortal(agent, 3);
        return true;
    }

    private static boolean royalSeal(AgentRuntimeEntry entry, Character agent,
                                     AgentMushroomKingdomState state,
                                     PrimitiveCapabilityGateway gateway, long nowMs) {
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.require(2331);
        if (gateway.questStatus(agent, 2331) == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (gateway.itemCount(agent, 4001318) < 1) {
            if (gateway.mapId(agent) == 106021600) {
                if (!nearPortal(entry, agent, 1, gateway)) return true;
                gateway.enterPortal(agent, 1);
                return true;
            }
            if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return true;
            if (!nearPortal(entry, agent, 2, gateway)) return true;
            gateway.stop(entry);
            gateway.enterPortal(agent, 2);
            return true;
        }
        return ordinary(entry, agent, node, state, gateway, nowMs);
    }

    private static boolean truthAndReturn(AgentRuntimeEntry entry, Character agent,
                                          AgentMushroomKingdomState state,
                                          PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.questStatus(agent, 2336) == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!ensurePrincessMap(entry, agent, state, gateway, nowMs)) return true;
            return interact(entry, agent, 1300002, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, 2336, 1300002));
        }
        if (!travel(entry, agent, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                state, gateway, nowMs)) return true;
        return interact(entry, agent, 1300000, state, gateway, nowMs,
                () -> gateway.completeQuest(agent, 2336, 1300000));
    }

    private static boolean ensurePrincessMap(AgentRuntimeEntry entry, Character agent,
                                             AgentMushroomKingdomState state,
                                             PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.mapId(agent) == 106021600) return true;
        if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return false;
        if (!nearPortal(entry, agent, 2, gateway)) return false;
        gateway.stop(entry);
        if (!gateway.enterPortal(agent, 2)) {
            capabilityFailure(entry, state, gateway,
                    "could not re-enter the rescued Princess room", nowMs);
            return false;
        }
        return gateway.mapId(agent) == 106021600;
    }

    private static boolean travel(AgentRuntimeEntry entry, Character agent, int mapId,
                                  AgentMushroomKingdomState state,
                                  PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.mapId(agent) == mapId) {
            state.capabilityProgress();
            return true;
        }
        AgentRouteOutcome outcome = gateway.travelTo(entry, agent, mapId, nowMs);
        if (outcome.status() == AgentRouteStatus.ARRIVED || outcome.status() == AgentRouteStatus.MOVING) {
            state.capabilityProgress();
            return outcome.status() == AgentRouteStatus.ARRIVED;
        }
        gateway.refreshNavigation(entry, agent);
        capabilityFailure(entry, state, gateway, "route " + gateway.mapId(agent) + " -> " + mapId
                + " returned " + outcome.status(), nowMs);
        return false;
    }

    private static boolean interact(AgentRuntimeEntry entry, Character agent, int npcId,
                                    AgentMushroomKingdomState state,
                                    PrimitiveCapabilityGateway gateway, long nowMs, Action action) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return capabilityFailure(entry, state, gateway,
                    "NPC " + npcId + " is absent from map " + gateway.mapId(agent), nowMs);
        }
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, INTERACTION_DISTANCE_PX)) {
            gateway.navigate(entry, npc, true);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) return true;
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        boolean success = action.run();
        state.nextActionAtMs(nowMs + INTERACTION_RETRY_MS);
        if (success) state.capabilityProgress();
        else capabilityFailure(entry, state, gateway,
                "NPC " + npcId + " did not advance quest " + state.currentQuestId(), nowMs);
        return true;
    }

    private static boolean nearPortal(AgentRuntimeEntry entry, Character agent, int portalId,
                                      PrimitiveCapabilityGateway gateway) {
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) return false;
        Point position = gateway.position(agent);
        if (!gateway.grounded(agent) || position == null || position.distance(portal) > 120.0d) {
            gateway.navigate(entry, portal, true);
            return false;
        }
        return true;
    }

    private static boolean capabilityFailure(AgentRuntimeEntry entry,
                                             AgentMushroomKingdomState state,
                                             PrimitiveCapabilityGateway gateway,
                                             String reason, long nowMs) {
        if (state.capabilityFailure() >= 8) {
            block(entry, state, gateway, reason);
            return false;
        }
        state.active(reason + "; retrying");
        state.nextActionAtMs(nowMs + INTERACTION_RETRY_MS);
        return true;
    }

    private static boolean block(AgentRuntimeEntry entry, AgentMushroomKingdomState state,
                                 PrimitiveCapabilityGateway gateway, String reason) {
        gateway.stop(entry);
        state.block(reason);
        return false;
    }

    private static int objectiveMetric(Character agent,
                                       AgentMushroomKingdomCatalog.QuestNode node,
                                       PrimitiveCapabilityGateway gateway) {
        if (node.itemId() > 0) return gateway.itemCount(agent, node.itemId());
        if (!node.mobIds().isEmpty()) {
            return node.mobIds().stream()
                    .mapToInt(mob -> gateway.questProgress(agent, node.questId(), mob)).sum();
        }
        return gateway.questStatus(agent, node.questId());
    }

    private static String reason(AgentMushroomKingdomCatalog.QuestNode node, int metric) {
        return node.hunting() ? "quest " + node.questId() + " objective " + metric + '/'
                + node.requiredCount() : "quest " + node.questId() + " dialogue/travel";
    }

    @FunctionalInterface
    private interface Action { boolean run(); }
}
