package server.agents.capabilities.townlife;

import client.Character;
import constants.id.ItemId;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.progression.AgentVictoriaRouteRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.List;

public final class AgentTownLifeRuntime {
    private static final int SHANKS_INTERACTION_DISTANCE_PX = 90;
    private static final int ACTIVITY_ARRIVAL_DISTANCE_PX = 70;
    private static final int ACTIVITY_ARRIVAL_VERTICAL_DISTANCE_PX = 12;
    private static final int MAP_SEAT_ARRIVAL_DISTANCE_PX = 12;

    private AgentTownLifeRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY)
                .map(AgentTownLifeState::enabled)
                .orElse(false);
    }

    /**
     * Returns true when this tick is fully consumed. A false result while active means
     * the ordinary movement phase should advance the move target selected here.
     */
    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        if (entry == null || agent == null || gateway == null) {
            return false;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return false;
        }
        if (state.stage() == AgentTownLifeState.Stage.TRAVEL_TO_LITH) {
            return travelToLith(entry, agent, state, nowMs, gateway);
        }
        if (agent.getMapId() != LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID
                && state.stage() != AgentTownLifeState.Stage.VISIT_SHOP
                && state.stage() != AgentTownLifeState.Stage.RETURN_FROM_SHOP
                && !(state.stage() == AgentTownLifeState.Stage.DWELL
                && state.activity() == AgentTownLifeState.Activity.SHOP_VISIT)) {
            state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
        }
        return switch (state.stage()) {
            case DISABLED -> false;
            case TRAVEL_TO_LITH -> true;
            case COMPLETE_ISLAND_HANDOFF -> completeIslandHandoff(
                    entry, agent, state, nowMs, gateway);
            case SETTLING -> tickSettling(entry, agent, state, nowMs, gateway);
            case CHOOSE_ACTIVITY -> chooseActivity(entry, agent, state, nowMs, gateway);
            case MOVE_TO_ACTIVITY -> moveToActivity(entry, agent, state, nowMs, gateway);
            case DWELL -> tickDwell(entry, agent, state, nowMs, gateway);
            case VISIT_SHOP -> visitShop(entry, agent, state, nowMs, gateway);
            case RETURN_FROM_SHOP -> returnFromShop(entry, agent, state, nowMs, gateway);
        };
    }

    public static void stop(AgentRuntimeEntry entry, Character agent) {
        if (entry == null) {
            return;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.stop();
        AgentFidgetService.clear(entry);
        if (agent != null && agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
    }

    private static boolean travelToLith(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentTownLifeState state,
                                        long nowMs,
                                        PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() == LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID) {
            gateway.stop(entry);
            state.transition(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF,
                    nowMs + delay(agent, state, 1_500, 3_501));
            return true;
        }
        if (agent.getMapId() != MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                    entry, agent, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID, gateway, nowMs);
            return outcome.status() != AgentVictoriaRouteRuntime.Status.MOVING;
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        Point shanks = gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        if (shanks == null) {
            state.transition(AgentTownLifeState.Stage.TRAVEL_TO_LITH, nowMs + 2_000L);
            return true;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(shanks)
                > SHANKS_INTERACTION_DISTANCE_PX * SHANKS_INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, shanks, true);
            return false;
        }
        gateway.stop(entry);
        gateway.facePosition(agent, shanks);
        boolean entered = gateway.runNpcScript(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        if (entered && agent.getMapId() == LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID) {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF,
                    nowMs + delay(agent, state, 1_500, 3_501));
        } else {
            state.transition(AgentTownLifeState.Stage.TRAVEL_TO_LITH, nowMs + 3_000L);
        }
        return true;
    }

    private static boolean completeIslandHandoff(AgentRuntimeEntry entry,
                                                 Character agent,
                                                 AgentTownLifeState state,
                                                 long nowMs,
                                                 PrimitiveCapabilityGateway gateway) {
        if (agent.getMapId() != LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID) {
            state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
            return true;
        }
        if (agent.getQuestStatus(MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID)
                != client.QuestStatus.Status.STARTED.getId()) {
            state.transition(AgentTownLifeState.Stage.SETTLING,
                    nowMs + delay(agent, state, 2_000, 5_001));
            return true;
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        Point biggs = gateway.npcPosition(agent, LithHarborTownLifeCatalog.BIGGS_NPC_ID);
        if (biggs == null) {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF, nowMs + 2_000L);
            return true;
        }
        if (!gateway.grounded(agent)
                || agent.getPosition().distanceSq(biggs)
                > SHANKS_INTERACTION_DISTANCE_PX * SHANKS_INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, biggs, true);
            return false;
        }
        gateway.stop(entry);
        gateway.facePosition(agent, biggs);
        boolean completed = gateway.canCompleteQuest(
                agent,
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID,
                LithHarborTownLifeCatalog.BIGGS_NPC_ID)
                && gateway.completeQuest(
                agent,
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID,
                LithHarborTownLifeCatalog.BIGGS_NPC_ID);
        if (completed || agent.getQuestStatus(
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID)
                == client.QuestStatus.Status.COMPLETED.getId()) {
            state.transition(AgentTownLifeState.Stage.SETTLING,
                    nowMs + delay(agent, state, 2_000, 5_001));
        } else {
            state.transition(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF, nowMs + 3_000L);
        }
        return true;
    }

    private static boolean tickSettling(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentTownLifeState state,
                                        long nowMs,
                                        PrimitiveCapabilityGateway gateway) {
        gateway.stop(entry);
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
        state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                nowMs + delay(agent, state, 900, 2_501));
        return true;
    }

    private static boolean chooseActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentFidgetService.clear(entry);
        int roll = varied(agent, state, 100, 17);
        AgentTownLifeState.Activity activity = roll < 20
                ? AgentTownLifeState.Activity.REST
                : roll < 45 ? AgentTownLifeState.Activity.SOCIAL
                : roll < 63 ? AgentTownLifeState.Activity.NPC_PAUSE
                : roll < 78 ? AgentTownLifeState.Activity.WANDER
                : roll < 92 ? AgentTownLifeState.Activity.SHOP_VISIT
                : AgentTownLifeState.Activity.WEAPON_FLOURISH;
        int sequence = state.sequence();
        if (activity == AgentTownLifeState.Activity.REST) {
            state.select(activity, LithHarborTownLifeCatalog.restSpot(varied(agent, state, 97, 31)),
                    0, 0, nowMs);
            return true;
        }
        if (activity == AgentTownLifeState.Activity.NPC_PAUSE) {
            LithHarborTownLifeCatalog.NpcSpot spot =
                    LithHarborTownLifeCatalog.npcSpot(varied(agent, state, 101, 47));
            Point npc = gateway.npcPosition(agent, spot.npcId());
            Point target = npc == null
                    ? LithHarborTownLifeCatalog.wanderSpot(sequence)
                    : new Point(npc.x + spot.offsetX(), npc.y);
            state.select(activity, target, 0, 0, nowMs);
            return true;
        }
        if (activity == AgentTownLifeState.Activity.SHOP_VISIT) {
            state.select(activity, null, 0,
                    LithHarborTownLifeCatalog.shopMapId(varied(agent, state, 103, 61)), nowMs);
            return true;
        }
        if (activity == AgentTownLifeState.Activity.SOCIAL
                || activity == AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            Character peer = choosePeer(agent, state);
            if (peer != null) {
                int side = varied(agent, state, 2, 73) == 0 ? -1 : 1;
                Point target = new Point(peer.getPosition().x + side * 52, peer.getPosition().y);
                state.select(activity, target, peer.getId(), 0, nowMs);
                return true;
            }
            activity = AgentTownLifeState.Activity.WANDER;
        }
        state.select(activity, LithHarborTownLifeCatalog.wanderSpot(varied(agent, state, 109, 89)),
                0, 0, nowMs);
        return true;
    }

    private static boolean moveToActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        Point target = state.target();
        if (target == null) {
            state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY, nowMs);
            return true;
        }
        int arrivalDistance = state.activity() == AgentTownLifeState.Activity.REST
                && LithHarborTownLifeCatalog.mapSeatId(target) >= 0
                ? MAP_SEAT_ARRIVAL_DISTANCE_PX
                : ACTIVITY_ARRIVAL_DISTANCE_PX;
        if (!gateway.grounded(agent)
                || Math.abs(agent.getPosition().y - target.y) > ACTIVITY_ARRIVAL_VERTICAL_DISTANCE_PX
                || agent.getPosition().distanceSq(target) > arrivalDistance * arrivalDistance) {
            gateway.navigate(entry, target, false);
            return false;
        }
        gateway.stop(entry);
        Point facing = peerPosition(state, agent);
        gateway.facePosition(agent, facing == null ? target : facing);
        long dwellMs = switch (state.activity()) {
            case REST -> delay(agent, state, 12_000, 36_001);
            case SOCIAL -> delay(agent, state, 5_000, 13_001);
            case NPC_PAUSE -> delay(agent, state, 4_000, 11_001);
            case WANDER -> delay(agent, state, 3_000, 9_001);
            case WEAPON_FLOURISH -> delay(agent, state, 3_000, 7_001);
            default -> 4_000L;
        };
        state.beginDwell(nowMs + dwellMs);
        beginDwellMotion(entry, agent, state, nowMs);
        return true;
    }

    private static boolean tickDwell(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentTownLifeState state,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        if (nowMs >= state.nextActionAtMs()) {
            AgentFidgetService.clear(entry);
            if (agent.getChair() >= 0) {
                AgentChairService.stand(entry, agent);
            }
            if (state.activity() == AgentTownLifeState.Activity.SHOP_VISIT
                    && agent.getMapId() != LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID) {
                state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
            } else {
                state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                        nowMs + delay(agent, state, 1_000, 4_501));
            }
            return true;
        }
        Point facing = peerPosition(state, agent);
        if (facing == null) {
            facing = state.target() == null ? agent.getPosition() : state.target();
        }
        gateway.facePosition(agent, facing);
        if (!state.expressionShown()) {
            agent.changeFaceExpression(expressionFor(agent, state));
            state.markExpressionShown();
        }
        if (state.activity() == AgentTownLifeState.Activity.REST) {
            if (agent.getChair() < 0) {
                int mapSeatId = LithHarborTownLifeCatalog.mapSeatId(state.target());
                if (mapSeatId >= 0) {
                    gateway.sitMapSeat(agent, mapSeatId, state.target());
                } else if (gateway.itemCount(agent, ItemId.RELAXER) > 0) {
                    gateway.sitChair(agent, ItemId.RELAXER);
                }
            }
            return true;
        }
        if (state.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH
                && !state.flourishShown()) {
            AgentTownLifeVisualService.flourish(agent, facing);
            state.markFlourishShown();
        }
        if (AgentFidgetStateRuntime.active(entry)) {
            AgentFidgetService.tryHandleTownLifeTick(entry, facing, nowMs);
        }
        return true;
    }

    private static boolean visitShop(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentTownLifeState state,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, state.destinationMapId(), gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.ARRIVED) {
            gateway.stop(entry);
            state.beginDwell(nowMs + delay(agent, state, 8_000, 21_001));
            beginDwellMotion(entry, agent, state, nowMs);
            return true;
        }
        return outcome.status() != AgentVictoriaRouteRuntime.Status.MOVING;
    }

    private static boolean returnFromShop(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID, gateway, nowMs);
        if (outcome.status() == AgentVictoriaRouteRuntime.Status.ARRIVED) {
            gateway.stop(entry);
            state.transition(AgentTownLifeState.Stage.SETTLING,
                    nowMs + delay(agent, state, 1_500, 4_501));
            return true;
        }
        return outcome.status() != AgentVictoriaRouteRuntime.Status.MOVING;
    }

    private static void beginDwellMotion(AgentRuntimeEntry entry,
                                         Character agent,
                                         AgentTownLifeState state,
                                         long nowMs) {
        AgentFidgetMode mode = switch (state.activity()) {
            case SOCIAL -> varied(agent, state, 3, 107) == 0
                    ? AgentFidgetMode.DIAGONAL_JUMP : AgentFidgetMode.SPAM_PRONE;
            case NPC_PAUSE -> varied(agent, state, 2, 109) == 0
                    ? AgentFidgetMode.PRONE : AgentFidgetMode.WAIT;
            case WANDER -> varied(agent, state, 4, 113) == 0
                    ? AgentFidgetMode.JUMP : AgentFidgetMode.WAIT;
            case SHOP_VISIT, WEAPON_FLOURISH -> AgentFidgetMode.WAIT;
            default -> AgentFidgetMode.NONE;
        };
        if (mode != AgentFidgetMode.NONE) {
            int duration = (int) Math.max(2_000L, state.nextActionAtMs() - nowMs);
            AgentFidgetService.startFidget(entry, mode, nowMs, duration, AgentFidgetTrigger.TOWN_LIFE);
        }
    }

    private static int expressionFor(Character agent, AgentTownLifeState state) {
        return switch (state.activity()) {
            case REST, WANDER, SHOP_VISIT -> AgentEmote.HAPPY.getValue();
            case NPC_PAUSE -> varied(agent, state, 2, 127) == 0
                    ? AgentEmote.GLARE.getValue() : AgentEmote.DISTURBED.getValue();
            case SOCIAL -> varied(agent, state, 3, 131) == 0
                    ? AgentEmote.ANNOYED.getValue() : AgentEmote.HAPPY.getValue();
            case WEAPON_FLOURISH -> varied(agent, state, 2, 137) == 0
                    ? AgentEmote.GLARE.getValue() : AgentEmote.ANGRY.getValue();
            default -> AgentEmote.HAPPY.getValue();
        };
    }

    private static Character choosePeer(Character agent, AgentTownLifeState state) {
        List<Character> peers = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(peer -> peer != null && peer != agent)
                .filter(peer -> peer.getMapId() == agent.getMapId())
                .filter(peer -> active(AgentRuntimeRegistry.findByCharacterInstance(peer)))
                .sorted(java.util.Comparator.comparingInt(Character::getId))
                .toList();
        if (peers.isEmpty()) {
            return null;
        }
        return peers.get(varied(agent, state, peers.size(), 149));
    }

    private static Point peerPosition(AgentTownLifeState state, Character agent) {
        if (state.targetCharacterId() <= 0) {
            return null;
        }
        AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(state.targetCharacterId());
        Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
        if (peer == null || peer.getMapId() != agent.getMapId()) {
            return null;
        }
        return new Point(peer.getPosition());
    }

    private static long delay(Character agent,
                              AgentTownLifeState state,
                              int minimumInclusive,
                              int maximumExclusive) {
        return minimumInclusive + varied(agent, state, maximumExclusive - minimumInclusive, 157);
    }

    private static int varied(Character agent, AgentTownLifeState state, int bound, int salt) {
        if (bound <= 1) {
            return 0;
        }
        long value = agent.getId() * 0x9E3779B97F4A7C15L
                + (long) state.sequence() * 0xBF58476D1CE4E5B9L
                + salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return Math.floorMod((int) value, bound);
    }
}
