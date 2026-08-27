package server.agents.capabilities.looting;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.maps.MapItem;

/** Gives King Pepe class boxes a short relevant-Explorer pickup priority window. */
final class AgentYetiClassBoxPriorityPolicy {
    static final long RELEVANT_CLASS_PRIORITY_MS = 15_000L;
    static final long HUMAN_CLASS_PRIORITY_MS = 7_000L;
    private static final int YETI_INSTANCE_MAP_ID = 106_021_500;
    private static final int FIRST_BOX_ITEM_ID = 2_022_570;
    private static final int LAST_BOX_ITEM_ID = 2_022_584;

    private AgentYetiClassBoxPriorityPolicy() {
    }

    static long minimumTargetAgeMs(Character bot, MapItem drop, long ordinaryMinimumMs) {
        if (bot == null || drop == null || bot.getMapId() != YETI_INSTANCE_MAP_ID
                || !classBox(drop.getItemId())) {
            return ordinaryMinimumMs;
        }
        boolean relevantHumanPresent = bot.getPartyMembersOnline().stream()
                .anyMatch(member -> member != null
                        && member.getMapId() == YETI_INSTANCE_MAP_ID
                        && !AgentCharacterGatewayRuntime.characters().isHeadlessControlled(member)
                        && matchesClass(member, drop.getItemId()));
        if (relevantHumanPresent) {
            return Math.max(ordinaryMinimumMs, HUMAN_CLASS_PRIORITY_MS);
        }
        if (matchesClass(bot, drop.getItemId())) return ordinaryMinimumMs;
        boolean relevantAgentPresent = bot.getPartyMembersOnline().stream()
                .anyMatch(member -> member != null
                        && member.getMapId() == YETI_INSTANCE_MAP_ID
                        && AgentCharacterGatewayRuntime.characters().isHeadlessControlled(member)
                        && matchesClass(member, drop.getItemId()));
        return relevantAgentPresent
                ? Math.max(ordinaryMinimumMs, RELEVANT_CLASS_PRIORITY_MS)
                : ordinaryMinimumMs;
    }

    static boolean matchesClass(Character character, int itemId) {
        if (character == null || character.getJob() == null || !classBox(itemId)) return false;
        int explorerFamily = character.getJob().getId() / 100;
        int boxFamily = Math.floorMod(itemId - FIRST_BOX_ITEM_ID, 5) + 1;
        return explorerFamily == boxFamily;
    }

    private static boolean classBox(int itemId) {
        return itemId >= FIRST_BOX_ITEM_ID && itemId <= LAST_BOX_ITEM_ID;
    }
}
