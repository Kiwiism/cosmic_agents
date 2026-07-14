package server.agents.testing;

import client.Character;
import server.agents.integration.AgentCharacterStateSnapshot;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Reactor;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MutablePrimitiveGatewayFixture {
    public final Character agent = mock(Character.class);
    public final AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
    public final PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
    public final AtomicInteger mapId = new AtomicInteger(10000);
    public final AtomicInteger level = new AtomicInteger(1);
    public final AtomicInteger experience = new AtomicInteger();
    public final AtomicInteger reactorHits = new AtomicInteger();
    public final Map<Integer, Integer> quests = new HashMap<>();
    public final Map<Integer, Integer> items = new HashMap<>();
    public final Map<String, Integer> progress = new HashMap<>();
    public final Map<Integer, Integer> questCompletions = new HashMap<>();
    public final Map<Integer, Integer> questExpRewards = new HashMap<>();
    public final Map<Integer, Integer> reactorRewards = new HashMap<>();
    public final Map<Integer, Integer> lootRewards = new HashMap<>();
    public final Set<String> directPortalEdges = new java.util.HashSet<>();
    public Point position = new Point(0, 0);
    public int combatQuestId;
    public int reactorHitsRequired = 1;
    private int pendingPortalDestination;

    public MutablePrimitiveGatewayFixture() {
        when(agent.getId()).thenReturn(77);
        when(agent.getLevel()).thenAnswer(ignored -> level.get());
        when(agent.getExp()).thenAnswer(ignored -> experience.get());
        when(gateway.mapId(agent)).thenAnswer(ignored -> mapId.get());
        when(gateway.position(agent)).thenAnswer(ignored -> new Point(position));
        when(gateway.alive(agent)).thenReturn(true);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.characterState(agent)).thenReturn(new AgentCharacterStateSnapshot(
                0, 1, 50, 50, 5, 5, true));
        when(gateway.questStatus(any(), anyInt())).thenAnswer(invocation ->
                quests.getOrDefault(invocation.getArgument(1), 0));
        when(gateway.questProgress(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                progress.getOrDefault(key(invocation.getArgument(1), invocation.getArgument(2)), 0));
        when(gateway.canStartQuest(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                quests.getOrDefault(invocation.getArgument(1), 0) == 0);
        when(gateway.canCompleteQuest(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                quests.getOrDefault(invocation.getArgument(1), 0) == 1);
        when(gateway.itemCount(any(), anyInt())).thenAnswer(invocation ->
                items.getOrDefault(invocation.getArgument(1), 0));
        when(gateway.freeSlots(any(), anyInt())).thenReturn(10);
        when(gateway.questItem(anyInt())).thenReturn(true);
        when(gateway.portalPresent(any(), anyInt())).thenReturn(true);
        when(gateway.portalPosition(any(), anyInt())).thenReturn(new Point(100, 0));
        when(gateway.directPortalIdTo(any(), anyInt())).thenAnswer(invocation -> {
            int destination = invocation.getArgument(1);
            if (!directPortalEdges.isEmpty() && !directPortalEdges.contains(mapId.get() + ":" + destination)) {
                return null;
            }
            pendingPortalDestination = destination;
            return 0;
        });
        when(gateway.enterPortal(any(), anyInt())).thenAnswer(invocation -> {
            mapId.set(pendingPortalDestination);
            return true;
        });
        when(gateway.npcPosition(any(), anyInt())).thenReturn(new Point(20, 0));
        when(gateway.nearestActiveReactorPosition(any(), any(), any())).thenReturn(new Point(30, 0));
        when(gateway.liveMonsterCount(any(), any())).thenReturn(1);
        when(gateway.interactNpc(any(), anyInt(), any(), any())).thenReturn(true);
        when(gateway.startQuest(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int questId = invocation.getArgument(1);
            if (questId == 1030) {
                quests.put(questId, 2);
            } else {
                quests.put(questId, 1);
            }
            if (questId == 1021) {
                items.put(2010007, 1);
            }
            return true;
        });
        when(gateway.completeQuest(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int questId = invocation.getArgument(1);
            quests.put(questId, 2);
            questCompletions.merge(questId, 1, Integer::sum);
            experience.addAndGet(questExpRewards.getOrDefault(questId, 0));
            items.replaceAll((id, count) -> 0);
            return true;
        });
        when(gateway.forceCompleteQuest(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int questId = invocation.getArgument(1);
            quests.put(questId, 2);
            questCompletions.merge(questId, 1, Integer::sum);
            return true;
        });
        when(gateway.beginFieldAbsence(any(), anyLong())).thenReturn(true);
        when(gateway.endFieldAbsence(any())).thenReturn(true);
        when(gateway.useItem(any(), anyInt())).thenAnswer(invocation -> {
            int itemId = invocation.getArgument(1);
            items.computeIfPresent(itemId, (id, count) -> Math.max(0, count - 1));
            return true;
        });
        doAnswer(invocation -> {
            position = new Point(invocation.getArgument(1));
            return null;
        }).when(gateway).navigate(any(), any(), any(Boolean.class));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Set<Integer> mobs = invocation.getArgument(1);
            mobs.forEach(mobId -> progress.put(key(combatQuestId, mobId), 999));
            return null;
        }).when(gateway).grind(any(), any());
        when(gateway.hitReactor(any(), anyInt())).thenAnswer(invocation -> {
            if (reactorHits.incrementAndGet() >= reactorHitsRequired) {
                items.putAll(reactorRewards);
            }
            return true;
        });
        when(gateway.lootNearby(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Set<Integer> requested = invocation.getArgument(1);
            requested.forEach(itemId -> {
                Integer count = lootRewards.get(itemId);
                if (count != null) {
                    items.put(itemId, count);
                }
            });
            return true;
        });
        when(gateway.sitChair(any(), anyInt())).thenReturn(true);

        Reactor reactor = mock(Reactor.class);
        when(reactor.isAlive()).thenAnswer(ignored -> reactorHits.get() < reactorHitsRequired);
        when(reactor.isActive()).thenAnswer(ignored -> reactorHits.get() < reactorHitsRequired);
        when(reactor.getObjectId()).thenReturn(900);
        when(reactor.getId()).thenReturn(2000);
        when(reactor.getName()).thenReturn("recycle");
        when(reactor.getPosition()).thenReturn(new Point(30, 0));
        when(gateway.reactors(any())).thenReturn(java.util.List.of(reactor));
    }

    public static String key(int questId, int progressId) {
        return questId + ":" + progressId;
    }
}
