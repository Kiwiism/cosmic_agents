package server.agents.integration;

import client.Character;
import client.inventory.Item;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import server.life.Monster;
import server.maps.MapleMap;
import server.maps.Mist;

import java.awt.Point;
import java.util.Map;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Cosmic packet broadcast and channel writes are thread-safe producer operations.")
public interface PacketGateway {
    void broadcastMovePlayer(Character agent, byte[] movementData);

    void broadcastChair(Character agent, int itemId);

    void broadcastCloseRangeAttack(Character agent,
                                   int skill,
                                   int skillLevel,
                                   int stance,
                                   int numAttackedAndDamage,
                                   Map<Integer, AttackTarget> targets,
                                   int speed,
                                   int direction,
                                   int display);

    void sendRemoveMist(Character recipient, int objectId);

    void sendRemoveItemFromMap(Character recipient, int objectId, int animation, int fromCharacterId);

    void sendTradeItemAdd(Character recipient, byte tradeNumber, Item item);

    void broadcastDamagePlayer(Character agent,
                               int damageFrom,
                               int monsterId,
                               int damage,
                               int fake,
                               int direction,
                               boolean pgmr,
                               int pgmr1,
                               boolean is_pg,
                               int oid,
                               int pos_x,
                               int pos_y);

    void sendMistFakeSpawn(Character recipient, Mist mist, int level);

    void sendWhisperReceive(Character recipient, String senderName, int channel, boolean gm, String message);

    void broadcastChatText(Character speaker, String message, boolean gm, int show);

    void broadcastSpawnMonster(MapleMap map, Monster monster, boolean newSpawn);

    void broadcastKillMonster(MapleMap map, int objectId, int animation, Point position);
}

