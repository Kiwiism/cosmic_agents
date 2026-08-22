package server.agents.runtime.activity.control.binding;

import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.plans.AgentPlanEntryRequest;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.commerce.AgentCommerceVisitRequest;
import server.agents.runtime.field.AgentFieldEntryRequest;

/** Typed, validated destination request compiled from one durable Director directive. */
public sealed interface AgentWorldTypedActivityRequest permits
        AgentWorldTypedActivityRequest.Questing,
        AgentWorldTypedActivityRequest.Hunting,
        AgentWorldTypedActivityRequest.TownLife,
        AgentWorldTypedActivityRequest.Commerce,
        AgentWorldTypedActivityRequest.PartyQuest {

    AgentActivityKind kind();

    record Questing(AgentPlanEntryRequest request) implements AgentWorldTypedActivityRequest {
        public Questing {
            if (request == null) throw new IllegalArgumentException("quest request is required");
        }
        @Override public AgentActivityKind kind() { return AgentActivityKind.QUESTING; }
    }

    record Hunting(AgentFieldEntryRequest request) implements AgentWorldTypedActivityRequest {
        public Hunting {
            if (request == null) throw new IllegalArgumentException("field request is required");
        }
        @Override public AgentActivityKind kind() { return AgentActivityKind.HUNTING; }
    }

    record TownLife(AgentTownLifeEntryRequest request) implements AgentWorldTypedActivityRequest {
        public TownLife {
            if (request == null) throw new IllegalArgumentException("TownLife request is required");
        }
        @Override public AgentActivityKind kind() { return AgentActivityKind.TOWN_LIFE; }
    }

    record Commerce(AgentCommerceVisitRequest request) implements AgentWorldTypedActivityRequest {
        public Commerce {
            if (request == null) throw new IllegalArgumentException("Commerce request is required");
        }
        @Override public AgentActivityKind kind() { return AgentActivityKind.COMMERCE; }
    }

    record PartyQuest(AgentPartyQuestVisitRequest request)
            implements AgentWorldTypedActivityRequest {
        public PartyQuest {
            if (request == null) throw new IllegalArgumentException("party-quest request is required");
        }
        @Override public AgentActivityKind kind() { return AgentActivityKind.PARTY_QUEST; }
    }

    /** Director-facing request; the party-quest admission service remains the aggregate owner. */
    record AgentPartyQuestVisitRequest(
            String requestId,
            String callerId,
            String scenarioId,
            int partySize,
            int maximumRuns) {
        public AgentPartyQuestVisitRequest {
            requestId = required(requestId, "party-quest request id");
            callerId = required(callerId, "party-quest caller id");
            scenarioId = required(scenarioId, "party-quest scenario id");
            if (partySize < 1 || maximumRuns < 1) {
                throw new IllegalArgumentException("positive party size and run count are required");
            }
        }
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
