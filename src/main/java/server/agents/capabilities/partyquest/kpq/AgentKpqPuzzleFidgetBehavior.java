package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/** Bounded presentation movement that never participates in puzzle correctness. */
final class AgentKpqPuzzleFidgetBehavior {
    private static final PrimitiveCapabilityGateway ACTIONS =
            AgentPrimitiveCapabilityGatewayRuntime.gateway();

    private AgentKpqPuzzleFidgetBehavior() {
    }

    static AgentKpqMemberState select(
            AgentKpqSession session, List<AgentKpqMemberState> participants) {
        List<AgentKpqMemberState> agents = participants.stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .toList();
        if (agents.isEmpty()) return null;
        int choice = Math.floorMod(session.seed() + session.attemptId() * 17L,
                agents.size() + 1);
        return choice == agents.size() ? null : agents.get(choice);
    }

    static void begin(AgentKpqMemberState member,
                      AgentKpqDefinition.CombinationStage definition,
                      Point current,
                      long seed,
                      int attemptId,
                      long nowMs,
                      long maximumDurationMs) {
        member.setFidgetedAttemptId(attemptId);
        member.beginFidget(target(definition, member, current, seed, attemptId),
                nowMs + Math.max(0L, maximumDurationMs));
    }

    static void tick(AgentKpqMemberState member,
                     Character agent,
                     AgentRuntimeEntry entry,
                     AgentKpqDefinition.CombinationStage definition,
                     long nowMs) {
        Point target = member.fidgetTarget();
        if (target == null || entry == null) return;
        if (nowMs >= member.fidgetUntilMs()
                || !definition.contains(member.assignedPosition(), target)) {
            member.clearFidget();
            return;
        }
        if (near(agent.getPosition(), target, 3)) {
            ACTIONS.stop(entry);
            member.clearFidget();
        } else {
            ACTIONS.navigate(entry, target, true);
        }
    }

    static Point target(AgentKpqDefinition.CombinationStage definition,
                        AgentKpqMemberState member,
                        Point current,
                        long seed,
                        int attemptId) {
        Rectangle area = definition.positions().get(member.assignedPosition() - 1);
        long mixed = seed + member.characterId() * 53L + attemptId * 109L;
        int direction = Math.floorMod(mixed, 2L) == 0L ? -1 : 1;
        if (definition.holdMode() == AgentKpqDefinition.HoldMode.ROPE) {
            int amount = 5 + Math.floorMod(mixed >>> 2, 8);
            int y = Math.max(area.y + 8, Math.min(area.y + area.height - 8,
                    current.y + direction * amount));
            return new Point((int) area.getCenterX(), y);
        }
        int maximum = definition.stageNumber() == 4 ? 5 : 10;
        int amount = 2 + Math.floorMod(mixed >>> 2, Math.max(1, maximum - 1));
        int x = Math.max(area.x + 3, Math.min(area.x + area.width - 3,
                current.x + direction * amount));
        return new Point(x, current.y);
    }

    private static boolean near(Point first, Point second, int px) {
        return first != null && second != null
                && Math.abs(first.x - second.x) <= px && Math.abs(first.y - second.y) <= px;
    }
}
