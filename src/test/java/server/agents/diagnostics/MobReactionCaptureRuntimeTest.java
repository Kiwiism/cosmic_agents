package server.agents.diagnostics;

import client.Character;
import client.Client;
import io.netty.buffer.Unpooled;
import net.opcodes.RecvOpcode;
import net.opcodes.SendOpcode;
import net.packet.ByteBufInPacket;
import net.packet.ByteBufOutPacket;
import net.packet.InPacket;
import net.packet.OutPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobReactionCaptureRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void correlatesSelectedMobPacketsRecipientsDamageAndMarkersInOneReport() throws Exception {
        Fixture fixture = fixture(930_001, 930_002, 930_003, 930_004, 100000000);
        String previousDirectory = System.getProperty("agents.mob.reaction.capture.dir");
        System.setProperty("agents.mob.reaction.capture.dir", temporaryDirectory.toString());

        try {
            assertTrue(MobReactionCaptureRuntime.start(
                    fixture.owner, fixture.map, fixture.monster, 20).success());
            assertTrue(MobReactionCaptureRuntime.mark(fixture.owner, "other-player-hit").success());

            byte[] moveLifeBody = moveLifeBody(fixture.monsterOid);
            InPacket incoming = new ByteBufInPacket(Unpooled.wrappedBuffer(moveLifeBody));
            MobReactionCaptureRuntime.recordInbound(
                    fixture.senderClient, RecvOpcode.MOVE_LIFE.getValue(), incoming);
            moveLifeBody[0] = 0;

            MobReactionCaptureRuntime.recordOutbound(fixture.recipientClient,
                    moveMonster(fixture.monsterOid));
            OutPacket attackPacket = closeRangeAttack(fixture.sender.getId(), fixture.monsterOid, 1234);
            MobReactionCaptureRuntime.recordBroadcast(fixture.map, fixture.sender, attackPacket);
            MobReactionCaptureRuntime.recordOutbound(fixture.recipientClient, attackPacket);
            MobReactionCaptureRuntime.recordDamage(fixture.sender, fixture.monster, 1234, false);
            MobReactionCaptureRuntime.recordOutbound(fixture.recipientClient,
                    unrelatedPacket());

            MobReactionCaptureRuntime.Status status = MobReactionCaptureRuntime.status(fixture.owner);
            assertTrue(status.active());
            assertEquals(6, status.eventCount());
            assertEquals("Controller", status.controllerName());

            MobReactionCaptureRuntime.StopResult stopped = MobReactionCaptureRuntime.stop(fixture.owner);
            assertTrue(stopped.success());
            assertEquals(6, stopped.eventCount());
            assertNotNull(stopped.reportPath());
            assertTrue(Files.isRegularFile(stopped.reportPath()));

            String report = Files.readString(stopped.reportPath());
            assertTrue(report.contains("kind=MARK"));
            assertTrue(report.contains("detail: other-player-hit"));
            assertTrue(report.contains("opcode=0x00BC(MOVE_LIFE)"));
            assertTrue(report.contains("opcode=0x00EF(MOVE_MONSTER)"));
            assertTrue(report.contains("opcode=0x00BA(CLOSE_RANGE_ATTACK)"));
            assertTrue(report.contains("kind=SERVER_BROADCAST"));
            assertTrue(report.contains("recipient=map-broadcast"));
            assertTrue(report.contains("actor=Sender#930003"));
            assertTrue(report.contains("recipient=Observer#930004"));
            assertTrue(report.contains("requestedDamage=1234 remainingHp=8766 killed=false"));
            assertTrue(report.contains("rawPacket"));
            assertTrue(report.contains("BC 00 22 32 0E 00"),
                    "capture must retain the original full MOVE_LIFE payload including opcode");
            assertFalse(report.contains("opcode=0x0011(PING)"), "unrelated packets must not be retained");
            assertFalse(MobReactionCaptureRuntime.status(fixture.owner).active());
        } finally {
            MobReactionCaptureRuntime.clear(fixture.owner);
            restoreProperty("agents.mob.reaction.capture.dir", previousDirectory);
        }
    }

    @Test
    void ignoresOtherMobAndMapTrafficAndEnforcesEventLimit() {
        Fixture fixture = fixture(940_001, 940_002, 940_003, 940_004, 101000000);
        Character otherOwner = character(940_010, "OtherOwner", fixture.map, new Point());

        try {
            assertTrue(MobReactionCaptureRuntime.start(
                    fixture.owner, fixture.map, fixture.monster, 2).success());
            assertFalse(MobReactionCaptureRuntime.start(
                    otherOwner, fixture.map, fixture.monster, 10).success());

            MobReactionCaptureRuntime.recordInbound(
                    fixture.senderClient,
                    RecvOpcode.MOVE_LIFE.getValue(),
                    new ByteBufInPacket(Unpooled.wrappedBuffer(moveLifeBody(fixture.monsterOid + 1))));
            assertEquals(0, MobReactionCaptureRuntime.status(fixture.owner).eventCount());

            assertTrue(MobReactionCaptureRuntime.mark(fixture.owner, "first").success());
            MobReactionCaptureRuntime.recordOutbound(fixture.recipientClient,
                    moveMonster(fixture.monsterOid));
            MobReactionCaptureRuntime.recordDamage(fixture.sender, fixture.monster, 100, false);

            MobReactionCaptureRuntime.Status status = MobReactionCaptureRuntime.status(fixture.owner);
            assertEquals(2, status.eventCount());
            assertTrue(status.limitReached());
            assertFalse(MobReactionCaptureRuntime.mark(fixture.owner, "too-late").success());
            assertTrue(MobReactionCaptureRuntime.clear(fixture.owner).success());
            assertFalse(MobReactionCaptureRuntime.status(fixture.owner).active());
        } finally {
            MobReactionCaptureRuntime.clear(fixture.owner);
            MobReactionCaptureRuntime.clear(otherOwner);
        }
    }

    @Test
    void capsOversizedRetainedPacketAndReportsOriginalLength() throws Exception {
        Fixture fixture = fixture(950_001, 950_002, 950_003, 950_004, 102000000);
        String previousDirectory = System.getProperty("agents.mob.reaction.capture.dir");
        System.setProperty("agents.mob.reaction.capture.dir", temporaryDirectory.toString());

        try {
            assertTrue(MobReactionCaptureRuntime.start(
                    fixture.owner, fixture.map, fixture.monster, 2).success());
            byte[] oversizedBody = new byte[20_000];
            oversizedBody[0] = (byte) fixture.monsterOid;
            oversizedBody[1] = (byte) (fixture.monsterOid >>> 8);
            oversizedBody[2] = (byte) (fixture.monsterOid >>> 16);
            oversizedBody[3] = (byte) (fixture.monsterOid >>> 24);
            MobReactionCaptureRuntime.recordInbound(
                    fixture.senderClient,
                    RecvOpcode.MOVE_LIFE.getValue(),
                    new ByteBufInPacket(Unpooled.wrappedBuffer(oversizedBody)));

            MobReactionCaptureRuntime.StopResult stopped = MobReactionCaptureRuntime.stop(fixture.owner);
            assertTrue(stopped.success());
            String report = Files.readString(stopped.reportPath());
            assertTrue(report.contains(
                    "packetTruncated=true originalBytes=20002 retainedBytes=16384"));
            assertTrue(report.contains("rawPacket[16384]"));
        } finally {
            MobReactionCaptureRuntime.clear(fixture.owner);
            restoreProperty("agents.mob.reaction.capture.dir", previousDirectory);
        }
    }

    private static Fixture fixture(int ownerId,
                                   int controllerId,
                                   int senderId,
                                   int recipientId,
                                   int mapId) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(mapId);
        Character owner = character(ownerId, "Owner", map, new Point(0, 0));
        Character controller = character(controllerId, "Controller", map, new Point(50, 100));
        Character sender = character(senderId, "Sender", map, new Point(20, 100));
        Character recipient = character(recipientId, "Observer", map, new Point(100, 100));

        int monsterOid = 930_338;
        Monster monster = mock(Monster.class);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(monsterOid);
        when(monster.getId()).thenReturn(100100);
        when(monster.getName()).thenReturn("Snail");
        when(monster.getPosition()).thenReturn(new Point(80, 100));
        when(monster.getStance()).thenReturn(5);
        when(monster.getFh()).thenReturn(12);
        when(monster.getHp()).thenReturn(8766);
        when(monster.getController()).thenReturn(controller);
        when(monster.isControllerHasAggro()).thenReturn(true);
        when(monster.isControllerKnowsAboutAggro()).thenReturn(true);
        when(map.getMonsterByOid(monsterOid)).thenReturn(monster);
        when(map.getCharacterById(senderId)).thenReturn(sender);

        Client senderClient = mock(Client.class);
        when(senderClient.getPlayer()).thenReturn(sender);
        Client recipientClient = mock(Client.class);
        when(recipientClient.getPlayer()).thenReturn(recipient);
        return new Fixture(owner, map, monster, monsterOid, sender, senderClient, recipientClient);
    }

    private static Character character(int id, String name, MapleMap map, Point position) {
        Character character = mock(Character.class);
        int mapId = map.getId();
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMap()).thenReturn(map);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(position);
        return character;
    }

    private static byte[] moveLifeBody(int monsterOid) {
        OutPacket packet = new ByteBufOutPacket();
        packet.writeInt(monsterOid);
        packet.writeShort(17);
        packet.writeByte(0);
        packet.writeByte(7);
        packet.writeByte(0);
        packet.writeByte(0);
        packet.writeShort(0);
        packet.skip(8);
        packet.writeByte(0);
        packet.writeInt(0);
        packet.writeShort(80);
        packet.writeShort(102);
        packet.writeByte(1);
        packet.writeByte(1);
        packet.writeShort(4);
        packet.writeShort(0);
        packet.writeByte(5);
        packet.writeShort(350);
        return packet.getBytes();
    }

    private static OutPacket moveMonster(int monsterOid) {
        OutPacket packet = OutPacket.create(SendOpcode.MOVE_MONSTER);
        packet.writeInt(monsterOid);
        packet.writeByte(0);
        packet.writeBool(true);
        packet.writeByte(7);
        packet.writeByte(0);
        packet.writeByte(0);
        packet.writeShort(0);
        packet.writeShort(80);
        packet.writeShort(100);
        packet.writeByte(1);
        packet.writeByte(1);
        packet.writeShort(4);
        packet.writeShort(0);
        packet.writeByte(5);
        packet.writeShort(350);
        return packet;
    }

    private static OutPacket closeRangeAttack(int attackerId, int monsterOid, int damage) {
        OutPacket packet = OutPacket.create(SendOpcode.CLOSE_RANGE_ATTACK);
        packet.writeInt(attackerId);
        packet.writeByte(0x11);
        packet.writeByte(0x5B);
        packet.writeByte(0);
        packet.writeByte(0);
        packet.writeByte(0);
        packet.writeByte(5);
        packet.writeByte(4);
        packet.writeByte(0x0A);
        packet.writeInt(0);
        packet.writeInt(monsterOid);
        packet.writeByte(0);
        packet.writeInt(damage);
        return packet;
    }

    private static OutPacket unrelatedPacket() {
        return OutPacket.create(SendOpcode.PING);
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private record Fixture(Character owner,
                           MapleMap map,
                           Monster monster,
                           int monsterOid,
                           Character sender,
                           Client senderClient,
                           Client recipientClient) {
    }
}
