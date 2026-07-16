package server.agents.diagnostics;

import client.Client;
import config.YamlConfig;
import net.opcodes.SendOpcode;
import net.packet.OutPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTransitionPacketTraceRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesBoundedOpcodeTraceWhenClientDropsAfterWarp() throws Exception {
        boolean previousEnabled = YamlConfig.config.server.AGENT_MAP_TRANSITION_PACKET_DIAGNOSTICS;
        String previousDirectory = System.getProperty("agents.map.transition.trace.dir");
        YamlConfig.config.server.AGENT_MAP_TRANSITION_PACKET_DIAGNOSTICS = true;
        System.setProperty("agents.map.transition.trace.dir", temporaryDirectory.toString());
        Client client = Client.createMock();

        try {
            MapTransitionPacketTraceRuntime.begin(client, 100000000, 1010100);
            MapTransitionPacketTraceRuntime.mark(client, "PLAYER_MAP_TRANSFER received");
            MapTransitionPacketTraceRuntime.recordOutbound(
                    client, OutPacket.create(SendOpcode.SPAWN_MONSTER));
            MapTransitionPacketTraceRuntime.complete(client);
            MapTransitionPacketTraceRuntime.disconnected(client, "channel inactive");

            Path report;
            try (var paths = Files.list(temporaryDirectory)) {
                report = paths.findFirst().orElseThrow();
            }
            String content = Files.readString(report);
            assertTrue(content.contains("fromMap=100000000"));
            assertTrue(content.contains("toMap=1010100"));
            assertTrue(content.contains("SPAWN_MONSTER"));
            assertTrue(content.contains("transitionCompleted=true"));
            assertTrue(content.contains("DISCONNECT channel inactive"));
        } finally {
            MapTransitionPacketTraceRuntime.clearForTest();
            YamlConfig.config.server.AGENT_MAP_TRANSITION_PACKET_DIAGNOSTICS = previousEnabled;
            if (previousDirectory == null) {
                System.clearProperty("agents.map.transition.trace.dir");
            } else {
                System.setProperty("agents.map.transition.trace.dir", previousDirectory);
            }
        }
    }
}
