package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import server.agents.capabilities.partyquest.kpq.AgentKpqDefinition;
import server.agents.plans.AgentPlanSessionHandle;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentCommerceControlRuntime;
import server.agents.runtime.activity.AgentActivityHostState;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.adapter.FieldActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.PartyQuestActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.QuestPlanActivitySessionAdapter;
import server.agents.runtime.activity.session.adapter.TownLifeActivitySessionAdapter;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.quest.Quest;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only Cosmic boundary for World Director preparation and diagnostics. */
public final class CosmicAgentWorldContextFactory {
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();

    private CosmicAgentWorldContextFactory() {
    }

    public static AgentWorldContext capture(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || nowMs < 0L) {
            throw new IllegalArgumentException("live Agent entry and valid capture time are required");
        }
        Set<Integer> activeQuests = new LinkedHashSet<>();
        Set<Integer> completedQuests = new LinkedHashSet<>();
        for (Quest quest : Quest.allQuests()) {
            int questId = quest.getId();
            int status = agent.getQuestStatus(questId);
            if (status == QuestStatus.Status.STARTED.getId()) {
                activeQuests.add(questId);
            } else if (status == QuestStatus.Status.COMPLETED.getId()) {
                completedQuests.add(questId);
            }
        }

        AgentActivityHostState host = entry.capabilityStates()
                .find(AgentActivityHostState.STATE_KEY).orElse(null);
        AgentPlanSessionHandle plan = AgentUniversalPlanRuntime.sessionHandle(entry);
        AgentActivitySessionSnapshot activity = sessionSnapshot(
                entry, agent, host == null ? null : host.activityKind(), nowMs);
        String careerStage = entry.capabilityStates()
                .find(AgentCareerProgressionState.STATE_KEY)
                .map(state -> state.stage().name()).orElse("");
        AgentActivityKind observedKind = activity != null
                ? activity.kind() : host == null ? null : host.activityKind();
        return new AgentWorldContext(
                NEXT_SEQUENCE.incrementAndGet(), nowMs, agent.getId(), agent.getName(),
                agent.getLevel(), agent.getJob().getId(), agent.getMapId(),
                Math.max(0, agent.getHp()), Math.max(0, agent.getMaxHp()),
                Math.max(0, agent.getMp()), Math.max(0, agent.getMaxMp()),
                Math.max(0, agent.getMeso()), agent.isAlive(),
                agent.getItemQuantity(AgentKpqDefinition.SQUISHY_SHOES, true) > 0,
                activeQuests, completedQuests,
                observedKind,
                host == null ? "" : host.controllerId(),
                activity == null ? "" : activity.sessionId(),
                plan == null ? "" : plan.planId(),
                careerStage,
                Map.of("captureMode", "read-only", "sessionGeneration",
                        Long.toString(entry.sessionGeneration())));
    }

    private static AgentActivitySessionSnapshot sessionSnapshot(
            AgentRuntimeEntry entry, Character agent, AgentActivityKind kind, long nowMs) {
        if (kind == null) return null;
        return switch (kind) {
            case QUESTING -> new QuestPlanActivitySessionAdapter(entry, agent, null)
                    .snapshot(nowMs);
            case HUNTING -> new FieldActivitySessionAdapter(entry, agent, null, null)
                    .snapshot(nowMs);
            case TOWN_LIFE -> new TownLifeActivitySessionAdapter(
                    entry, agent, null, null, agent.getId()).snapshot(nowMs);
            case COMMERCE -> commerceSnapshot(agent.getId(), nowMs);
            case PARTY_QUEST -> new PartyQuestActivitySessionAdapter(agent.getId(), null)
                    .snapshot(nowMs);
        };
    }

    private static AgentActivitySessionSnapshot commerceSnapshot(int characterId, long nowMs) {
        AgentActivitySessionSnapshot managed =
                AgentCommerceSessionRegistryRuntime.snapshot(characterId, nowMs);
        return managed.phase().retainsSession() || !AgentCommerceControlRuntime.claimed(characterId)
                ? managed : AgentCommerceControlRuntime.snapshot(characterId);
    }
}
