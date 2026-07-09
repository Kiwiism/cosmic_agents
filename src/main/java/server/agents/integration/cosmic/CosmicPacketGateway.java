package server.agents.integration.cosmic;

import client.Character;
import client.inventory.Item;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import server.agents.integration.PacketGateway;
import server.maps.Mist;
import tools.PacketCreator;

import java.util.Map;

/**
 * Cosmic packet boundary for Agent packet construction and broadcast.
 */
public enum CosmicPacketGateway implements PacketGateway {
    INSTANCE;

    @Override
    public void broadcastMovePlayer(Character agent, byte[] movementData) {
        if (agent == null || agent.getMap() == null || movementData == null) {
            return;
        }
        InPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(movementData));
        Packet movePacket = PacketCreator.movePlayer(agent.getId(), packet, movementData.length);
        agent.getMap().broadcastMessage(agent, movePacket, false);
    }

    @Override
    public void broadcastCloseRangeAttack(Character agent,
                                          int skill,
                                          int skillLevel,
                                          int stance,
                                          int numAttackedAndDamage,
                                          Map<Integer, AttackTarget> targets,
                                          int speed,
                                          int direction,
                                          int display) {
        if (agent == null || agent.getMap() == null) {
            return;
        }
        Packet attackPacket = PacketCreator.closeRangeAttack(
                agent,
                skill,
                skillLevel,
                stance,
                numAttackedAndDamage,
                targets,
                speed,
                direction,
                display);
        agent.getMap().broadcastMessage(agent, attackPacket, false);
    }

    @Override
    public void sendRemoveMist(Character recipient, int objectId) {
        if (recipient == null) {
            return;
        }
        recipient.sendPacket(PacketCreator.removeMist(objectId));
    }

    @Override
    public void sendRemoveItemFromMap(Character recipient, int objectId, int animation, int fromCharacterId) {
        if (recipient == null) {
            return;
        }
        recipient.sendPacket(PacketCreator.removeItemFromMap(objectId, animation, fromCharacterId));
    }

    @Override
    public void sendTradeItemAdd(Character recipient, byte tradeNumber, Item item) {
        if (recipient == null || item == null) {
            return;
        }
        recipient.sendPacket(PacketCreator.getTradeItemAdd(tradeNumber, item));
    }

    @Override
    public void broadcastDamagePlayer(Character agent,
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
                                      int pos_y) {
        if (agent == null || agent.getMap() == null) {
            return;
        }
        agent.getMap().broadcastMessage(agent,
                PacketCreator.damagePlayer(damageFrom, monsterId, agent.getId(), damage, fake,
                        direction, pgmr, pgmr1, is_pg, oid, pos_x, pos_y), false);
    }

    @Override
    public void sendMistFakeSpawn(Character recipient, Mist mist, int level) {
        if (recipient == null || mist == null) {
            return;
        }
        recipient.sendPacket(mist.makeFakeSpawnData(level));
    }
}
