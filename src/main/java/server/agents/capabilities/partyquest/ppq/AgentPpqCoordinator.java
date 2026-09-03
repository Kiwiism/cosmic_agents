package server.agents.capabilities.partyquest.ppq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.perception.AgentMapPerception;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapItem;
import server.maps.Reactor;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Comparator;
import java.util.Set;

/** Natural-action PPQ coordinator: clear, collect, optional chests, doors, boss. */
public final class AgentPpqCoordinator {
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway PPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final long EVENT_TIMEOUT_MS = 30L * 60_000L;
    private static final long STALL_TIMEOUT_MS = 10L * 60_000L;
    private static final long RETRY_MS = 900L;
    private static final int INTERACTION_RADIUS = 70;

    private AgentPpqCoordinator() { }

    public static void tickSession(AgentPpqSession session, long nowMs) {
        if (session.terminal() || session.paused()) return;
        if (nowMs - session.startedAtMs() >= EVENT_TIMEOUT_MS) {
            session.fail("PPQ event timer expired", nowMs); return;
        }
        Character leader = character(session.eventLeaderId());
        if (leader == null || leader.getHp() <= 0) { session.fail("PPQ event leader is unavailable", nowMs); return; }
        if (session.eventInstance() == null && leader.getEventInstance() != null
                && AgentPpqDefinition.isEventMap(leader.getMapId())) {
            session.bindEventInstance(leader.getEventInstance());
            session.transition(AgentPpqSession.Phase.ACTIVE, nowMs);
        }
        if (session.eventInstance() != null && session.eventInstance().isEventCleared()
                && session.phase().ordinal() < AgentPpqSession.Phase.CLEARING.ordinal()) {
            session.transition(AgentPpqSession.Phase.CLEARING, nowMs);
        }
        if (session.eventInstance() != null && session.eventInstance().isEventDisposed()
                && session.phase().ordinal() < AgentPpqSession.Phase.CLEARING.ordinal()) {
            session.fail("PPQ event instance was disposed before clear", nowMs); return;
        }
        if (session.mode() == AgentPpqSession.Mode.HUMAN_LEADER
                && session.phase() == AgentPpqSession.Phase.PREPARING) {
            announce(session, "human-enter", leader.getName() + ", talk to Guon to enter Pirate PQ.");
        }
        if (session.eventInstance() != null) {
            int openedChests = parseInt(PPQ.property(leader, "openedChests"));
            if (openedChests >= 1) session.markChestComplete(AgentPpqDefinition.CHEST_ONE_MAP, nowMs);
            if (openedChests >= 2) session.markChestComplete(AgentPpqDefinition.CHEST_TWO_MAP, nowMs);
        }
        if (session.mode() == AgentPpqSession.Mode.HUMAN_LEADER) guideHumanLeader(session, leader);
        if (leader.getMapId() == AgentPpqDefinition.BOSS_MAP
                && ACTIONS.liveMonsterCount(leader, AgentPpqDefinition.COMBAT_MOBS) == 0) {
            if (isAgent(leader)) runNpc(entry(leader.getId()), leader, AgentPpqDefinition.RESCUE_NPC);
            else announce(session, "human-clear", leader.getName() + ", talk to Wu Yang after Lord Pirate falls.");
        }
        if (leader.getMapId() == AgentPpqDefinition.CLEAR_MAP) {
            session.transition(AgentPpqSession.Phase.CLEARING, nowMs);
        }
        if (session.phase() == AgentPpqSession.Phase.CLEARING
                && session.members().stream().map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull).noneMatch(member -> member.getMapId() == AgentPpqDefinition.CLEAR_MAP)) {
            session.complete(nowMs);
        }
        // The authored medal stage allows six minutes and uses probabilistic drops.  Do not
        // declare a legal, still-attacking party stalled before that stage can naturally finish.
        if (nowMs - session.lastProgressAtMs() >= STALL_TIMEOUT_MS) session.fail("PPQ stalled", nowMs);
    }

    public static void tickMember(AgentPpqSession session, AgentRuntimeEntry entry,
                                  Character agent, long nowMs) {
        AgentPpqMemberState member = session.member(agent.getId());
        if (member == null || member.memberType() != AgentPpqMemberState.MemberType.AGENT
                || nowMs < member.nextActionAtMs()) return;
        if (session.eventInstance() != null && AgentPpqDefinition.isEventMap(agent.getMapId())
                && agent.getEventInstance() != session.eventInstance()) {
            session.fail("PPQ member entered a different event instance", nowMs); return;
        }
        int mapId = agent.getMapId();
        if (mapId == AgentPpqDefinition.RECRUIT_MAP) { enter(session, entry, agent, member, nowMs); return; }
        if (mapId == AgentPpqDefinition.MEDAL_MAP) { medals(session, entry, agent, member, nowMs); return; }
        if (mapId == AgentPpqDefinition.CHEST_ONE_MAP || mapId == AgentPpqDefinition.CHEST_TWO_MAP) {
            chest(session, entry, agent, member, nowMs); return;
        }
        if (mapId == AgentPpqDefinition.DOOR_MAP) { doors(session, entry, agent, member, nowMs); return; }
        if (mapId == AgentPpqDefinition.BOSS_MAP) { boss(session, entry, agent); return; }
        if (mapId == AgentPpqDefinition.CLEAR_MAP) {
            if (runNpc(entry, agent, AgentPpqDefinition.RESCUE_NPC, 0)) member.deferUntil(nowMs + RETRY_MS);
            return;
        }
        if (mapId == AgentPpqDefinition.ENTRY_MAP
                || mapId == AgentPpqDefinition.DECK_ONE_MAP || mapId == AgentPpqDefinition.DECK_TWO_MAP) {
            clearAndAdvance(session, entry, agent, member, nowMs); return;
        }
        ACTIONS.stop(entry);
    }

    private static void enter(AgentPpqSession session, AgentRuntimeEntry entry, Character agent,
                              AgentPpqMemberState member, long nowMs) {
        if (agent.getId() != session.eventLeaderId() || session.mode() == AgentPpqSession.Mode.HUMAN_LEADER) {
            ACTIONS.stop(entry); return;
        }
        if (runNpc(entry, agent, AgentPpqDefinition.ENTRY_NPC, 0)) {
            member.deferUntil(nowMs + RETRY_MS); session.transition(AgentPpqSession.Phase.ENTERING, nowMs);
        }
    }

    private static void medals(AgentPpqSession session, AgentRuntimeEntry entry, Character agent,
                               AgentPpqMemberState member, long nowMs) {
        Character leader = character(session.eventLeaderId());
        int itemId = expectedMedal(leader);
        if (agent.getId() == session.eventLeaderId()) {
            if (itemId != 0 && agent.getItemQuantity(itemId, false) >= AgentPpqDefinition.MEDALS_PER_WAVE) {
                if (runNpc(entry, agent, AgentPpqDefinition.GUIDE_NPC)) {
                    member.deferUntil(nowMs + RETRY_MS); session.markProgress(nowMs);
                }
                return;
            }
            // Submit as soon as the current-wave quota is met. Chasing every medal type first
            // can never drain this continuously respawning map and prevents the NPC hand-in.
            if (itemId != 0 && collectObjectiveDrop(
                    session, entry, agent, Set.of(itemId), nowMs)) return;
        }
        if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) {
            ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS); return;
        }
        if (itemId == 0) usePortal(entry, agent, member, AgentPpqDefinition.nextPortalId(agent.getMapId()), nowMs);
        else ACTIONS.stop(entry);
    }

    private static int expectedMedal(Character leader) {
        if (leader == null) return AgentPpqDefinition.ROOKIE_MEDAL;
        String stage = PPQ.property(leader, "stage2");
        return switch (stage == null ? "0" : stage) {
            case "0" -> AgentPpqDefinition.ROOKIE_MEDAL;
            case "1" -> AgentPpqDefinition.RISING_MEDAL;
            case "2" -> AgentPpqDefinition.VETERAN_MEDAL;
            default -> 0;
        };
    }

    private static void clearAndAdvance(AgentPpqSession session, AgentRuntimeEntry entry,
                                        Character agent, AgentPpqMemberState member, long nowMs) {
        if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) {
            ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS); return;
        }
        int mapId = agent.getMapId();
        boolean deck = mapId == AgentPpqDefinition.DECK_ONE_MAP || mapId == AgentPpqDefinition.DECK_TWO_MAP;
        Character leader = character(session.eventLeaderId());
        boolean chestAvailable = !session.skipChestRooms() && !session.chestCompleteForDeck(mapId)
                && leader != null && leader.getItemQuantity(AgentPpqDefinition.CHEST_KEY, false) > 0;
        if (deck && chestAvailable) {
            // Everyone enters to clear the optional room; only the leader performs the key drop.
            // Leaving four fighters idle makes the authored deck timer unnecessarily fragile.
            usePortal(entry, agent, member, 1, nowMs);
            return;
        }
        usePortal(entry, agent, member, AgentPpqDefinition.nextPortalId(mapId), nowMs);
    }

    private static void chest(AgentPpqSession session, AgentRuntimeEntry entry, Character agent,
                              AgentPpqMemberState member, long nowMs) {
        if (session.chestComplete(agent.getMapId())) {
            member.clearChestDrop();
            usePortal(entry, agent, member, 1, nowMs); return;
        }
        if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) {
            ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS); return;
        }
        if (agent.getId() != session.eventLeaderId()) {
            ACTIONS.stop(entry); return;
        }
        if (member.chestDropPending(agent.getMapId())) {
            ACTIONS.stop(entry); return;
        }
        Reactor chest = ACTIONS.reactors(agent).stream().filter(java.util.Objects::nonNull)
                .filter(Reactor::isAlive).filter(reactor -> AgentPpqDefinition.CHEST_REACTORS.contains(reactor.getId()))
                .findFirst().orElse(null);
        if (chest == null || !chest.isActive() || chest.getState() < 1) {
            if (runNpc(entry, agent, AgentPpqDefinition.GUIDE_NPC)) member.deferUntil(nowMs + RETRY_MS);
            return;
        }
        if (agent.getItemQuantity(AgentPpqDefinition.CHEST_KEY, false) > 0) {
            Point trigger = reactorDropPoint(chest);
            if (!chest.getArea().contains(agent.getPosition())) {
                Point ground = ACTIONS.groundPoint(agent.getMap(), trigger);
                if (ground != null) ACTIONS.navigate(entry, ground, true);
                return;
            }
            ACTIONS.stop(entry);
            if (AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                    AgentPpqDefinition.CHEST_KEY, (short) 1)) {
                member.beginChestDrop(agent.getMapId());
                member.deferUntil(nowMs + RETRY_MS);
                session.markProgress(nowMs);
            }
            return;
        }
        session.markChestComplete(agent.getMapId(), nowMs);
        usePortal(entry, agent, member, 1, nowMs);
    }

    private static void doors(AgentPpqSession session, AgentRuntimeEntry entry, Character agent,
                              AgentPpqMemberState member, long nowMs) {
        if (collectObjectiveDrop(session, entry, agent,
                Set.of(AgentPpqDefinition.OLD_METAL_KEY), nowMs)) return;
        int totalKeys = session.members().stream().map(memberState -> character(memberState.characterId()))
                .filter(java.util.Objects::nonNull)
                .mapToInt(character -> character.getItemQuantity(AgentPpqDefinition.OLD_METAL_KEY, false)).sum();
        int openDoors = (int) ACTIONS.reactors(agent).stream().filter(java.util.Objects::nonNull)
                .filter(reactor -> AgentPpqDefinition.DOOR_REACTORS.contains(reactor.getId()))
                .filter(reactor -> reactor.getState() < 1).count();
        // Preserve every authored spawn point until the party has enough keys for all remaining doors.
        if (totalKeys < openDoors) {
            if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS);
            else ACTIONS.stop(entry);
            return;
        }
        Reactor door = committedDoor(agent, member.committedReactorObjectId());
        if (door == null) door = nearestUnclaimedDoor(session, agent);
        if (door != null && agent.getItemQuantity(AgentPpqDefinition.OLD_METAL_KEY, false) > 0) {
            member.commitReactor(door.getObjectId());
            Point trigger = reactorDropPoint(door);
            if (!door.getArea().contains(agent.getPosition())) {
                Point ground = ACTIONS.groundPoint(agent.getMap(), trigger);
                if (ground != null) ACTIONS.navigate(entry, ground, true);
                return;
            }
            ACTIONS.stop(entry);
            if (AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                    AgentPpqDefinition.OLD_METAL_KEY, (short) 1)) {
                member.clearReactor(); member.deferUntil(nowMs + RETRY_MS); session.markProgress(nowMs);
            }
            return;
        }
        if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) {
            ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS); return;
        }
        if (openDoors == 0) usePortal(entry, agent, member, AgentPpqDefinition.nextPortalId(agent.getMapId()), nowMs);
        else ACTIONS.stop(entry);
    }

    /**
     * Objective drops must be pursued across the whole authored map.  A radius-only pickup can
     * strand medals or keys behind after ranged combat moves on to another platform.
     */
    private static boolean collectObjectiveDrop(AgentPpqSession session, AgentRuntimeEntry entry,
                                                Character agent, Set<Integer> itemIds, long nowMs) {
        if (agent.getMap() == null || agent.getPosition() == null) return false;
        MapItem target = AgentMapPerception.items(agent.getMap()).stream()
                .filter(drop -> !drop.isPickedUp() && itemIds.contains(drop.getItemId()))
                .filter(drop -> AgentLootEligibility.canBotTargetLoot(
                        entry, agent, agent.getMap(), drop, nowMs))
                .filter(drop -> nearestEligibleCollector(
                        session, drop, agent.getMapId(), nowMs) == agent.getId())
                .min(Comparator.comparingDouble(drop ->
                        drop.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null) return false;
        if (near(agent.getPosition(), target.getPosition())) {
            ACTIONS.stop(entry);
            int before = agent.getItemQuantity(target.getItemId(), false);
            agent.pickupItem(target);
            if (agent.getItemQuantity(target.getItemId(), false) > before) session.markProgress(nowMs);
        } else {
            Point ground = ACTIONS.groundPoint(agent.getMap(), target.getPosition());
            ACTIONS.navigate(entry, ground == null ? target.getPosition() : ground, true);
        }
        return true;
    }

    private static int nearestEligibleCollector(AgentPpqSession session, MapItem drop,
                                                int mapId, long nowMs) {
        return session.members().stream()
                .filter(member -> member.memberType() == AgentPpqMemberState.MemberType.AGENT)
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> candidate.getMapId() == mapId && candidate.getPosition() != null)
                .filter(candidate -> {
                    AgentRuntimeEntry candidateEntry = entry(candidate.getId());
                    return candidateEntry != null && AgentLootEligibility.canBotTargetLoot(
                            candidateEntry, candidate, candidate.getMap(), drop, nowMs);
                })
                .min(Comparator.comparingDouble((Character candidate) ->
                                candidate.getPosition().distanceSq(drop.getPosition()))
                        .thenComparingInt(Character::getId))
                .map(Character::getId)
                .orElse(0);
    }

    private static void boss(AgentPpqSession session, AgentRuntimeEntry entry, Character agent) {
        if (ACTIONS.liveMonsterCount(agent, AgentPpqDefinition.COMBAT_MOBS) > 0) ACTIONS.grind(entry, AgentPpqDefinition.COMBAT_MOBS);
        else if (agent.getId() != session.eventLeaderId()) ACTIONS.stop(entry);
    }

    private static Reactor committedDoor(Character agent, int objectId) {
        if (objectId == 0) return null;
        return ACTIONS.reactors(agent).stream().filter(java.util.Objects::nonNull)
                .filter(reactor -> reactor.getObjectId() == objectId && reactor.getState() < 1).findFirst().orElse(null);
    }
    private static Reactor nearestUnclaimedDoor(AgentPpqSession session, Character agent) {
        Set<Integer> claimed = session.members().stream().map(AgentPpqMemberState::committedReactorObjectId)
                .filter(id -> id > 0).collect(java.util.stream.Collectors.toSet());
        return ACTIONS.reactors(agent).stream().filter(java.util.Objects::nonNull)
                .filter(reactor -> AgentPpqDefinition.DOOR_REACTORS.contains(reactor.getId()) && reactor.getState() < 1)
                .filter(reactor -> !claimed.contains(reactor.getObjectId()))
                .min(Comparator.comparingDouble(reactor -> reactor.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
    }
    private static void usePortal(AgentRuntimeEntry entry, Character agent, AgentPpqMemberState member,
                                  int portalId, long nowMs) {
        if (portalId < 0) { ACTIONS.stop(entry); return; }
        Point portal = ACTIONS.portalPosition(agent, portalId);
        if (!near(agent.getPosition(), portal)) { if (portal != null) ACTIONS.navigate(entry, portal, true); return; }
        ACTIONS.stop(entry);
        if (PPQ.enterPortal(agent, portalId)) member.deferUntil(nowMs + RETRY_MS);
    }
    private static boolean runNpc(AgentRuntimeEntry entry, Character agent, int npcId, int... selections) {
        Point npc = ACTIONS.npcPosition(agent, npcId);
        if (!near(agent.getPosition(), npc)) { if (npc != null) ACTIONS.navigate(entry, npc, true); return false; }
        ACTIONS.stop(entry); return PPQ.runNpc(agent, npcId, selections);
    }
    private static void announce(AgentPpqSession session, String key, String message) {
        AgentPpqMemberState speaker = session.members().stream()
                .filter(member -> member.memberType() == AgentPpqMemberState.MemberType.AGENT)
                .filter(member -> member.claimAnnouncement(key)).findFirst().orElse(null);
        Character agent = speaker == null ? null : character(speaker.characterId());
        if (agent != null) AgentPartyGatewayRuntime.party().sendPartyChat(agent, message);
    }
    private static void guideHumanLeader(AgentPpqSession session, Character leader) {
        int mapId = leader.getMapId();
        if (mapId == AgentPpqDefinition.MEDAL_MAP) {
            int medal = expectedMedal(leader);
            if (medal != 0) announce(session, "human-medal-" + medal,
                    leader.getName() + ", loot 20 of item " + medal + " and talk to Guon. We will keep killing.");
        } else if (!session.skipChestRooms()
                && (mapId == AgentPpqDefinition.DECK_ONE_MAP || mapId == AgentPpqDefinition.DECK_TWO_MAP)
                && !session.chestCompleteForDeck(mapId)
                && leader.getItemQuantity(AgentPpqDefinition.CHEST_KEY, false) > 0
                && ACTIONS.liveMonsterCount(leader, AgentPpqDefinition.COMBAT_MOBS) == 0) {
            announce(session, "human-chest-" + mapId,
                    leader.getName() + ", take the side portal, clear the chest room, talk to Guon, and drop a chest key by the chest.");
        }
    }
    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return 0; }
    }
    private static boolean near(Point first, Point second) {
        return first != null && second != null && first.distanceSq(second) <= (long) INTERACTION_RADIUS * INTERACTION_RADIUS;
    }
    private static Point reactorDropPoint(Reactor reactor) {
        Rectangle area = reactor.getArea();
        int x = area.x + Math.max(0, area.width - 1) / 2;
        int y = Math.max(area.y, Math.min(reactor.getPosition().y,
                area.y + Math.max(0, area.height - 1)));
        return new Point(x, y);
    }
    private static boolean isAgent(Character character) { return character != null && entry(character.getId()) != null; }
    private static AgentRuntimeEntry entry(int id) { return AgentRuntimeRegistry.findByAgentCharacterId(id); }
    public static Character character(int id) {
        AgentRuntimeEntry runtime = entry(id);
        Character agent = runtime == null ? null : AgentRuntimeIdentityRuntime.bot(runtime);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
}
