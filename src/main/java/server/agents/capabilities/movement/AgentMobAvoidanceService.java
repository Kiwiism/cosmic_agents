package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import java.awt.Rectangle;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.life.Monster;

public final class AgentMobAvoidanceService {
    private AgentMobAvoidanceService() {
    }

    public static boolean shouldJumpToAvoidMob(AgentRuntimeEntry entry, Foothold currentFoothold, Point botPos, int stepX) {
        if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry)
                || currentFoothold == null || botPos == null || stepX == 0) {
            return false;
        }
        if ((!AgentModeStateRuntime.following(entry) && !AgentModeStateRuntime.grinding(entry))
                || AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                || AgentNavigationDebugStateRuntime.navPreciseTarget(entry)) {
            return false;
        }

        Monster blockingMob = firstBlockingMobInWalkLane(entry, currentFoothold, botPos, stepX);
        if (blockingMob == null) {
            return false;
        }

        return simulatedJumpLandsInCurrentRegion(entry, currentFoothold, botPos, stepX);
    }

    static Monster firstBlockingMobInWalkLane(AgentRuntimeEntry entry, Foothold currentFoothold, Point botPos, int stepX) {
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        int direction = Integer.signum(stepX);
        int lookahead = Math.max(Math.abs(stepX),
                AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry))
                        * Math.max(1, AgentMovementPhysicsConfig.configuredMobAvoidLookaheadSteps()));
        int laneEndX = botPos.x + direction * lookahead;
        Rectangle lane = inclusiveRectangle(
                Math.min(botPos.x, laneEndX),
                botPos.y - AgentCombatConfig.cfg.MOB_TOUCH_SWEEP_HEIGHT,
                Math.max(botPos.x, laneEndX),
                botPos.y);

        Monster nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Monster mob : server.agents.perception.AgentMapPerception.monsters(map)) {
            if (!mob.isAlive() || !isMobInCurrentGroundRegion(entry, currentFoothold, mob)) {
                continue;
            }

            Rectangle bounds = AgentMobHitboxProvider.getInstance().getMobBounds(mob);
            if (bounds == null) {
                bounds = inclusiveRectangle(mob.getPosition().x, mob.getPosition().y, mob.getPosition().x, mob.getPosition().y);
            }
            if (!lane.intersects(bounds) && !lane.contains(mob.getPosition())) {
                continue;
            }

            int mobEdgeX = direction > 0 ? bounds.x : bounds.x + bounds.width;
            int distance = Math.max(0, direction > 0 ? mobEdgeX - botPos.x : botPos.x - mobEdgeX);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = mob;
            }
        }
        return nearest;
    }

    static boolean isMobInCurrentGroundRegion(AgentRuntimeEntry entry, Foothold currentFoothold, Monster mob) {
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        Foothold mobFoothold = AgentGroundingService.findGroundFoothold(map, mob.getPosition());
        if (mobFoothold != null && mobFoothold.getId() == currentFoothold.getId()) {
            return true;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(
                map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return false;
        }

        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        int currentRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, bot.getPosition());
        int mobRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, mob.getPosition());
        return currentRegionId >= 0 && currentRegionId == mobRegionId;
    }

    static boolean simulatedJumpLandsInCurrentRegion(AgentRuntimeEntry entry, Foothold currentFoothold, Point botPos, int stepX) {
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        AgentMovementProfile profile = AgentMovementStateRuntime.movementProfile(entry);
        int airVelX = AgentJumpActionService.resolveAirVelocityX(map, profile, stepX);
        AgentJumpLanding landing = AgentJumpProbeService.simulateJumpLanding(map, botPos, airVelX, profile);
        if (landing == null || landing.point() == null || landing.foothold() == null) {
            return false;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return landing.foothold().getId() == currentFoothold.getId();
        }

        int currentRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, map, botPos);
        int landingRegionId = AgentNavigationRegionService.resolveTargetRegionId(graph, entry, map, landing.point());
        return currentRegionId >= 0 && currentRegionId == landingRegionId;
    }

    static Rectangle inclusiveRectangle(int left, int top, int right, int bottom) {
        return new Rectangle(left, top, Math.max(1, right - left + 1), Math.max(1, bottom - top + 1));
    }
}
