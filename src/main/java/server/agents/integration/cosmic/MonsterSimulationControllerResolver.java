package server.agents.integration.cosmic;

import client.BotClient;
import client.Character;
import server.integration.AgentPresence;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.Comparator;

final class MonsterSimulationControllerResolver {
    private MonsterSimulationControllerResolver() {
    }

    static Character resolve(Monster monster) {
        if (monster == null || monster.getMap() == null) {
            return null;
        }
        Character current = monster.getController();
        if (isEligible(current, monster.getMap())) {
            return current;
        }
        return monster.getMap().getAllPlayers().stream()
                .filter(candidate -> isEligible(candidate, monster.getMap()))
                .min(Comparator.comparingInt(Character::getNumControlledMonsters)
                        .thenComparingDouble(candidate -> candidate.getPosition()
                                .distanceSq(monster.getPosition()))
                        .thenComparingInt(Character::getId))
                .orElse(null);
    }

    static boolean hasObserver(MapleMap map) {
        return map != null && map.isObservedByPlayer()
                && map.getAllPlayers().stream().anyMatch(candidate -> isEligible(candidate, map));
    }

    static boolean isEligible(Character candidate, MapleMap map) {
        return candidate != null && candidate.getMap() == map && candidate.isLoggedinWorld()
                && !candidate.isChangingMaps() && !AgentPresence.isAgent(candidate)
                && !(candidate.getClient() instanceof BotClient);
    }
}
