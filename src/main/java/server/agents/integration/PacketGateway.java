package server.agents.integration;

import client.Character;
import client.inventory.Item;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import server.maps.Mist;

import java.util.Map;

public interface PacketGateway {
    void broadcastMovePlayer(Character agent, byte[] movementData);

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
}

