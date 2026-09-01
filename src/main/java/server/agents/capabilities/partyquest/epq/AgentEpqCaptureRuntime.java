package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import server.life.Monster;
import tools.PacketCreator;

/** Private EPQ implementation of the authored purification-marble catch action. */
final class AgentEpqCaptureRuntime {
    private AgentEpqCaptureRuntime() { }

    static boolean ready(Monster monster) {
        return monster != null && monster.isAlive()
                && monster.getId() == AgentEpqDefinition.POISON_FLOWER
                && monster.getHp() < (monster.getMaxHp() / 10L) * 4L;
    }

    static boolean capture(Character agent, Monster monster) {
        AgentEpqSession session = agent == null ? null : AgentEpqSessionRegistry.forMember(agent.getId());
        if (session == null || agent.getMapId() != AgentEpqDefinition.STAGE_FOUR_MAP
                || agent.getEventInstance() != session.eventInstance()
                || monster == null || agent.getMap().getMonsterByOid(monster.getObjectId()) != monster
                || !ready(monster)
                || agent.getInventory(InventoryType.USE).countById(AgentEpqDefinition.PURIFICATION_MARBLE) < 1
                || !agent.canHold(AgentEpqDefinition.MONSTER_MARBLE, 1)) {
            return false;
        }
        agent.getMap().broadcastMessage(PacketCreator.catchMonster(
                monster.getObjectId(), AgentEpqDefinition.PURIFICATION_MARBLE, (byte) 1));
        agent.getMap().killMonster(monster, null, false, (short) 0);
        InventoryManipulator.removeById(agent.getClient(), InventoryType.USE,
                AgentEpqDefinition.PURIFICATION_MARBLE, 1, true, true);
        InventoryManipulator.addById(agent.getClient(), AgentEpqDefinition.MONSTER_MARBLE,
                (short) 1, "", -1);
        agent.sendPacket(PacketCreator.enableActions());
        session.markProgress(System.currentTimeMillis());
        return true;
    }
}
