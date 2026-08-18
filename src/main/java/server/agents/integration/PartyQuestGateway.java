package server.agents.integration;

import client.Character;
import scripting.event.EventInstanceManager;

import java.util.Set;
import java.util.List;

/** Semantic boundary for authoritative party-quest event and NPC operations. */
@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "KPQ operations run through normal channel-owned NPC, portal, event, and loot paths.")
public interface PartyQuestGateway {
    boolean runNpc(Character agent, int npcId, int... selections);

    boolean enterPortal(Character agent, int portalId);

    boolean lootNearby(Character agent, Set<Integer> itemIds);

    EventInstanceManager event(Character character);

    String eventName(Character character);

    String property(Character character, String key);

    int playerGrid(Character character);

    boolean sameEvent(Character first, Character second);

    List<Character> eventMembers(Character character);
}
