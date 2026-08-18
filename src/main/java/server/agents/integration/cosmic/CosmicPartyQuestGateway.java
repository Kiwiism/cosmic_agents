package server.agents.integration.cosmic;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.integration.PartyQuestGateway;

import java.util.Set;
import java.util.List;

public enum CosmicPartyQuestGateway implements PartyQuestGateway {
    INSTANCE;

    @Override
    public boolean runNpc(Character agent, int npcId, int... selections) {
        return CosmicPrimitiveCapabilityGateway.INSTANCE.runNpcScript(agent, npcId, selections);
    }

    @Override
    public boolean enterPortal(Character agent, int portalId) {
        return CosmicMapGateway.INSTANCE.enterPortal(agent, portalId);
    }

    @Override
    public boolean lootNearby(Character agent, Set<Integer> itemIds) {
        return CosmicPrimitiveCapabilityGateway.INSTANCE.lootNearby(agent, itemIds);
    }

    @Override
    public EventInstanceManager event(Character character) {
        return character == null ? null : character.getEventInstance();
    }

    @Override
    public String eventName(Character character) {
        EventInstanceManager event = event(character);
        return event == null ? "" : event.getName();
    }

    @Override
    public String property(Character character, String key) {
        EventInstanceManager event = event(character);
        return event == null ? null : event.getProperty(key);
    }

    @Override
    public int playerGrid(Character character) {
        EventInstanceManager event = event(character);
        return event == null ? -1 : event.gridCheck(character);
    }

    @Override
    public boolean sameEvent(Character first, Character second) {
        EventInstanceManager firstEvent = event(first);
        return firstEvent != null && firstEvent == event(second);
    }

    @Override
    public List<Character> eventMembers(Character character) {
        EventInstanceManager event = event(character);
        return event == null ? List.of() : List.copyOf(event.getPlayers());
    }
}
