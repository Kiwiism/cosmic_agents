package server.agents.capabilities.partyquest.epq;

import client.Character;
import server.life.EpqPoisonFlowerCaptureService;
import server.life.Monster;

/** Private EPQ implementation of the authored purification-marble catch action. */
final class AgentEpqCaptureRuntime {
    private AgentEpqCaptureRuntime() { }

    static boolean ready(Monster monster) {
        return EpqPoisonFlowerCaptureService.ready(monster);
    }

    static boolean capture(Character agent, Monster monster) {
        AgentEpqSession session = agent == null ? null : AgentEpqSessionRegistry.forMember(agent.getId());
        if (session == null || agent.getMapId() != AgentEpqDefinition.STAGE_FOUR_MAP
                || agent.getEventInstance() != session.eventInstance()
                || monster == null || agent.getMap().getMonsterByOid(monster.getObjectId()) != monster) {
            return false;
        }
        if (EpqPoisonFlowerCaptureService.capture(agent, monster)
                != EpqPoisonFlowerCaptureService.Result.CAPTURED) return false;
        session.markProgress(System.currentTimeMillis());
        return true;
    }
}
