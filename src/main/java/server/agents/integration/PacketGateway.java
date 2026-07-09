package server.agents.integration;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;

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
}

