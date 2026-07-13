package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned formation model and offset application for live Agent groups.
 */
public final class AgentFormationService {
    public enum FormationType { STAGGER, RANDOM, STACK, SPREAD, LEFT, RIGHT }

    private static final Map<Integer, FormationState> formationsByLeaderId = new ConcurrentHashMap<>();

    public record FormationState(FormationType type, int px, int snapRange) {
        public int offsetFor(int idx, int total) {
            return switch (type) {
                case STAGGER -> (idx % 2 == 0 ? 1 : -1) * (idx / 2 + 1) * px;
                case RANDOM -> {
                    int range = px * total / 2;
                    yield range > 0 ? ThreadLocalRandom.current().nextInt(-range, range + 1) : 0;
                }
                case STACK -> 0;
                case SPREAD -> idx == 0 ? 0 : (idx % 2 == 1 ? 1 : -1) * ((idx + 1) / 2) * px;
                case LEFT -> -(idx + 1) * px;
                case RIGHT -> (idx + 1) * px;
            };
        }
    }

    private AgentFormationService() {
    }

    public static FormationState defaultStagger(int followStaggerPx, int snapRange) {
        return new FormationState(FormationType.STAGGER, followStaggerPx, snapRange);
    }

    public static Map<Integer, FormationState> formationsByLeaderId() {
        return formationsByLeaderId;
    }

    public static FormationState stateForEntry(AgentRuntimeEntry entry,
                                               Map<Integer, FormationState> formationsByLeaderId,
                                               FormationState defaultFormation) {
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        if (leader == null) {
            return defaultFormation;
        }
        return stateForLeader(formationsByLeaderId, leader.getId(), defaultFormation);
    }

    public static FormationState stateForLeader(Map<Integer, FormationState> formationsByLeaderId,
                                                int leaderCharId,
                                                FormationState defaultFormation) {
        return formationsByLeaderId.getOrDefault(leaderCharId, defaultFormation);
    }

    public static void applyOffsets(List<? extends AgentRuntimeEntry> entries, FormationState formation) {
        if (entries == null || formation == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            AgentRuntimeEntry entry = entries.get(i);
            int offset = formation.offsetFor(i, entries.size());
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                AgentFormationStateRuntime.setFollowOffsetX(entry, offset);
                return null;
            });
        }
    }
}
