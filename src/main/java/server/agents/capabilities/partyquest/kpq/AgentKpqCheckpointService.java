package server.agents.capabilities.partyquest.kpq;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

/** GM-test-only stage bootstrap. Never used by production KPQ sessions. */
final class AgentKpqCheckpointService {
    private AgentKpqCheckpointService() {
    }

    static void apply(AgentKpqSession session, Character leader, int stage, long nowMs) {
        if (session.mode() != AgentKpqSession.Mode.TEST_OBSERVATION) {
            throw new IllegalStateException("KPQ checkpoints are test-only");
        }
        EventInstanceManager event = leader.getEventInstance();
        if (event == null) {
            session.fail("Checkpoint requested without a live KPQ event", nowMs);
            return;
        }
        for (int cleared = 1; cleared < stage; cleared++) {
            event.setProperty(cleared + "stageclear", "true");
        }
        MapleMap target = event.getMapInstance(AgentKpqDefinition.STAGE_1_MAP + stage - 1);
        if (target == null) {
            session.fail("KPQ checkpoint map " + stage + " is unavailable", nowMs);
            return;
        }
        var spawn = target.getPortal(0).getPosition();
        for (AgentKpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
            if (agent != null) AgentMapGatewayRuntime.map().changeMap(agent, target, spawn);
        }
        session.transition(switch (stage) {
            case 2 -> AgentKpqSession.Phase.STAGE_2;
            case 3 -> AgentKpqSession.Phase.STAGE_3;
            case 4 -> AgentKpqSession.Phase.STAGE_4;
            case 5 -> AgentKpqSession.Phase.STAGE_5;
            default -> AgentKpqSession.Phase.STAGE_1;
        }, nowMs);
    }
}
