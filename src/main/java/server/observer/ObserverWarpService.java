package server.observer;

import client.Character;
import client.Client;
import server.observer.protocol.ObserverActionProtocol;
import server.maps.MapleMap;

public final class ObserverWarpService {
    public record Result(int status,
                         int mapId,
                         int characterId,
                         String message) {
    }

    private ObserverWarpService() {
    }

    public static Result warpMap(Client client, int mapId) {
        Character observer = client == null ? null : client.getPlayer();
        if (observer == null || mapId < 0) {
            return invalid("Invalid observer or map ID.");
        }
        if (!observer.isAlive()) {
            return blocked("The observer cannot warp while dead.");
        }

        MapleMap target = client.getChannelServer().getMapFactory().getMap(mapId);
        if (target == null) {
            return notFound("Map " + mapId + " was not found.");
        }

        observer.saveLocationOnWarp();
        observer.changeMap(target, target.getRandomPlayerSpawnpoint());
        return ok(target.getId(), 0, "Warped to map " + target.getId() + ".");
    }

    public static Result warpCharacter(Client client, int characterId) {
        Character observer = client == null ? null : client.getPlayer();
        if (observer == null || characterId <= 0 || characterId == observer.getId()) {
            return invalid("Invalid observer target.");
        }
        if (!observer.isAlive()) {
            return blocked("The observer cannot warp while dead.");
        }

        Character target = client.getWorldServer().getPlayerStorage()
                .getCharacterById(characterId);
        if (target == null
                || !target.isLoggedin()
                || target.getClient() == null
                || target.getMap() == null) {
            return notFound("The target character is not online.");
        }
        if (target.getClient().getChannel() != client.getChannel()) {
            return new Result(
                    ObserverActionProtocol.STATUS_WRONG_CHANNEL,
                    target.getMapId(),
                    target.getId(),
                    "Target is on channel " + target.getClient().getChannel() + ".");
        }

        MapleMap map = target.getMap();
        observer.saveLocationOnWarp();
        observer.forceChangeMap(
                map,
                map.findClosestPortal(target.getPosition()));
        return ok(map.getId(), target.getId(), "Warped to " + target.getName() + ".");
    }

    private static Result ok(int mapId, int characterId, String message) {
        return new Result(
                ObserverActionProtocol.STATUS_OK,
                mapId,
                characterId,
                message);
    }

    private static Result invalid(String message) {
        return new Result(
                ObserverActionProtocol.STATUS_INVALID_REQUEST,
                0,
                0,
                message);
    }

    private static Result notFound(String message) {
        return new Result(
                ObserverActionProtocol.STATUS_TARGET_NOT_FOUND,
                0,
                0,
                message);
    }

    private static Result blocked(String message) {
        return new Result(
                ObserverActionProtocol.STATUS_BLOCKED,
                0,
                0,
                message);
    }
}
