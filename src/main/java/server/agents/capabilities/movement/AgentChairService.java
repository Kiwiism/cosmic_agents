package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentChairService {
    private static final int CHAIR_MOVEMENT_COMMAND = 11;

    private AgentChairService() {
    }

    public static boolean sit(AgentRuntimeEntry entry, Character agent, int itemId) {
        if (entry == null || agent == null || itemId <= 0) {
            return false;
        }
        agent.sitChair(itemId);
        if (agent.getChair() != itemId) {
            return false;
        }
        AgentMovementStateRuntime.clearMoveDirection(entry);
        int stance = AgentMovementStateRuntime.facingDirectionSign(entry) < 0
                ? CharacterStance.SIT_LEFT_STANCE : CharacterStance.SIT_RIGHT_STANCE;
        broadcast(entry, agent, itemId, stance);
        return true;
    }

    public static boolean stand(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null) {
            return false;
        }
        agent.sitChair(-1);
        int stance = AgentMovementStateRuntime.facingDirectionSign(entry) < 0
                ? CharacterStance.STAND_LEFT_STANCE : CharacterStance.STAND_RIGHT_STANCE;
        broadcast(entry, agent, 0, stance);
        return agent.getChair() < 0;
    }

    static byte[] chairMovementData(Point position, int stance) {
        byte[] data = new byte[11];
        int offset = 0;
        data[offset++] = 1;
        data[offset++] = CHAIR_MOVEMENT_COMMAND;
        offset = putShort(data, offset, position.x);
        offset = putShort(data, offset, position.y);
        offset = putShort(data, offset, 0);
        data[offset++] = (byte) stance;
        putShort(data, offset, 0);
        return data;
    }

    private static void broadcast(AgentRuntimeEntry entry, Character agent, int itemId, int stance) {
        agent.setStance(stance);
        AgentMovementBroadcastStateRuntime.invalidate(entry);
        byte[] movement = chairMovementData(agent.getPosition(), stance);
        AgentPacketGatewayRuntime.packets().broadcastMovePlayer(agent, movement);
        AgentPacketGatewayRuntime.packets().broadcastChair(agent, itemId);
    }

    private static int putShort(byte[] data, int offset, int value) {
        data[offset++] = (byte) (value & 0xFF);
        data[offset++] = (byte) (value >> 8);
        return offset;
    }
}
