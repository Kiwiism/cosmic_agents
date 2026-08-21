package server.agents.runtime.activity.world;

/** Durable request identities; no request in preparation can be admitted. */
public enum AgentWorldActivityRequestType {
    AUTHORED_PLAN,
    INDIVIDUAL_QUEST,
    FIELD_VISIT,
    TOWN_LIFE_VISIT,
    COMMERCE_VISIT,
    PARTY_QUEST_VISIT,
    OBSERVE_ONLY
}
