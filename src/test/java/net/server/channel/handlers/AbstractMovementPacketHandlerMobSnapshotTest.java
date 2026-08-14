package net.server.channel.handlers;

import client.Client;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.simulation.MobMovementSnapshot;
import testutil.Packets;
import tools.exceptions.EmptyMovementException;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AbstractMovementPacketHandlerMobSnapshotTest {
    private final ExposedMovementHandler handler = new ExposedMovementHandler();

    @Test
    void absoluteMobMovementCapturesFootholdAndVelocity() throws EmptyMovementException {
        Monster monster = monsterAt(0, 0, 0);
        InPacket packet = Packets.buildInPacket(out -> {
            out.writeByte(1);
            out.writeByte(0);
            out.writeShort(50);
            out.writeShort(82);
            out.writeShort(125);
            out.writeShort(-250);
            out.writeShort(7);
            out.writeByte(2);
            out.writeShort(8);
        });

        handler.update(packet, monster, -2);

        MobMovementSnapshot snapshot = monster.getLastClientMovement();
        assertNotNull(snapshot);
        assertEquals(new Point(50, 80), monster.getPosition());
        assertEquals(7, monster.getFh());
        assertEquals(1.0, snapshot.velocityX(), 1.0e-12);
        assertEquals(-2.0, snapshot.velocityY(), 1.0e-12);
    }

    @Test
    void relativeAirMovementRefreshesVelocityWithoutMovingServerPosition() throws EmptyMovementException {
        Monster monster = monsterAt(50, 80, 7);
        InPacket packet = Packets.buildInPacket(out -> {
            out.writeByte(1);
            out.writeByte(1);
            out.writeShort(-125);
            out.writeShort(250);
            out.writeByte(3);
            out.writeShort(8);
        });

        handler.update(packet, monster, -2);

        MobMovementSnapshot snapshot = monster.getLastClientMovement();
        assertNotNull(snapshot);
        assertEquals(new Point(50, 80), monster.getPosition());
        assertEquals(-1.0, snapshot.velocityX(), 1.0e-12);
        assertEquals(2.0, snapshot.velocityY(), 1.0e-12);
    }

    private static Monster monsterAt(int x, int y, int footholdId) {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        Monster monster = new Monster(100100, stats);
        monster.setPosition(new Point(x, y));
        monster.setFh(footholdId);
        return monster;
    }

    private static final class ExposedMovementHandler extends AbstractMovementPacketHandler {
        @Override
        public void handlePacket(InPacket p, Client c) {
        }

        private void update(InPacket packet, Monster monster, int yOffset)
                throws EmptyMovementException {
            updatePosition(packet, monster, yOffset);
        }
    }
}
