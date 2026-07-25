package server.agents.observer;

import client.Character;
import client.QuestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.plans.mapleisland.AgentMapleIslandLithHandoffRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Independent controller for the Kiwi observer showcase.
 *
 * <p>The observer owns a private movement entry and scheduler. It is deliberately absent from
 * the Agent registry, plan executor, foreground arbiter, and TownLife runtime.</p>
 */
public final class AgentObserverRuntime {
    private static final String TUNING_PREFIX =
            "server.agents.observer.AgentObserverRuntime.";
    private static final Logger log = LoggerFactory.getLogger(AgentObserverRuntime.class);
    private static final Object LOCK = new Object();
    private static final AgentObserverMovementController MOVEMENT =
            new AgentObserverMovementController();
    private static final int TICK_MS = tuningInt("TICK_MS");
    private static final int SHADOW_DISTANCE_PX = tuningInt("SHADOW_DISTANCE_PX");
    private static final int INVESTIGATION_DISTANCE_PX = tuningInt("INVESTIGATION_DISTANCE_PX");

    private static AgentObserverSession active;
    private static ScheduledFuture<?> schedule;

    private AgentObserverRuntime() {
    }

    public static StartResult start(Character watched, String observerName, long nowMs) {
        if (watched == null || observerName == null || observerName.isBlank()) {
            return new StartResult(false, "Observer and watched character are required.");
        }
        String requestedObserverName = observerName.trim();
        AgentResolvedCharacter resolvedObserver;
        try {
            resolvedObserver = AgentPersistenceGatewayRuntime.persistence()
                    .findCharacterByName(requestedObserverName);
        } catch (SQLException failure) {
            log.warn("Could not resolve observer character '{}'", requestedObserverName, failure);
            return new StartResult(false, "Could not load observer character '"
                    + requestedObserverName + "'.");
        }
        if (resolvedObserver == null) {
            return new StartResult(false, "Observer character '" + requestedObserverName
                    + "' does not exist.");
        }
        if (resolvedObserver.id() == watched.getId()) {
            return new StartResult(false,
                    "The observer must be different from the controlled character.");
        }
        int world = AgentClientGatewayRuntime.clients().world(watched);
        int channel = AgentClientGatewayRuntime.clients().channel(watched);
        synchronized (LOCK) {
            if (active != null && active.observerId == resolvedObserver.id()) {
                stopLocked();
            }
            Character onlineObserver = online(resolvedObserver.id());
            if (onlineObserver != null) {
                return new StartResult(false, "Observer character '" + onlineObserver.getName()
                        + "' is already online. Log it out before starting the showcase.");
            }

            Character observer = null;
            try {
                MapleMap stationMap = AgentMapGatewayRuntime.map()
                        .resolveMap(world, channel, AgentObserverPolicy.STATION_MAP_ID);
                if (stationMap == null) {
                    return new StartResult(false, "Observer station map "
                            + AgentObserverPolicy.STATION_MAP_ID + " is unavailable.");
                }
                Portal stationPortal = stationMap.getPortal(0);
                Point stationPosition = stationPortal == null
                        ? null : new Point(stationPortal.getPosition());
                observer = CosmicAgentOfflineLoader.loadOfflineAgent(
                        resolvedObserver.id(), world, channel, stationMap, stationPosition);

                stopLocked();
                AgentRuntimeEntry movementEntry = new AgentRuntimeEntry(observer, null, null);
                active = new AgentObserverSession(
                        observer.getId(), watched.getId(), world, channel, movementEntry);
                active.lastObservedMapId = observer.getMapId();
                schedule = AgentSchedulerRuntime.register(
                        AgentObserverRuntime::guardedTick, TICK_MS);
                log.info("Started observer showcase observer={} watched={} stationMap={}",
                        observer.getName(), watched.getName(), AgentObserverPolicy.STATION_MAP_ID);
            } catch (SQLException | RuntimeException failure) {
                log.warn("Could not start observer showcase observer={} watched={}",
                        requestedObserverName, watched.getName(), failure);
                if (observer != null && active != null
                        && active.observerId == observer.getId()) {
                    stopLocked();
                } else {
                    disconnectLoadedObserver(observer);
                }
                return new StartResult(false, "Could not load observer character '"
                        + requestedObserverName + "'.");
            }
        }
        return new StartResult(true, requestedObserverName
                + " was loaded as the observer and stationed in Snail Garden. Roaming begins "
                + "when " + watched.getName() + " enters that map; F1 requests an investigation.");
    }

    public static String status() {
        synchronized (LOCK) {
            if (active == null) {
                return "No observer showcase is active.";
            }
            Character observer = online(active.observerId);
            Character watched = online(active.watchedId);
            return "Observer stage=" + active.stage
                    + ", observer=" + characterStatus(observer)
                    + ", watched=" + characterStatus(watched)
                    + ", destinationMap=" + active.destinationMapId
                    + ", BariComplete=" + active.bariCompleted
                    + ", handoffTriggered=" + active.handoffTriggered + ".";
        }
    }

    public static boolean stop() {
        synchronized (LOCK) {
            boolean stopped = active != null;
            stopLocked();
            return stopped;
        }
    }

    /** Called only after the live client successfully submits F1/emote 1. */
    public static void signalF1(Character source, long nowMs) {
        if (source == null) {
            return;
        }
        synchronized (LOCK) {
            if (active != null && active.watchedId == source.getId()) {
                active.requestInvestigation(nowMs);
            }
        }
    }

    private static void guardedTick() {
        try {
            synchronized (LOCK) {
                tickLocked(System.currentTimeMillis());
            }
        } catch (RuntimeException failure) {
            log.warn("Observer showcase tick failed", failure);
        }
    }

    private static void tickLocked(long nowMs) {
        AgentObserverSession session = active;
        if (session == null) {
            return;
        }
        Character observer = online(session.observerId);
        Character watched = online(session.watchedId);
        if (observer == null) {
            log.info("Stopping observer showcase because observer logged out");
            stopLocked();
            return;
        }
        if (watched == null) {
            MOVEMENT.stop(session);
            return;
        }

        observeMilestones(session, observer, watched, nowMs);
        if (session.investigationRequestedAtMs > 0
                && session.stage != AgentObserverSession.Stage.INVESTIGATING) {
            beginInvestigation(session, observer, watched, nowMs);
        }

        switch (session.stage) {
            case STATIONED -> tickStationed(session, observer, watched, nowMs);
            case APPROACH_ROAM -> tickApproachRoam(session, observer, nowMs);
            case MAPLE_CYCLE -> tickMapleCycle(session, observer, nowMs);
            case EXCURSION -> tickExcursion(session, observer, nowMs);
            case INVESTIGATING -> tickInvestigation(session, observer, watched, nowMs);
            case SHADOWING -> tickShadowing(session, observer, watched, nowMs);
            case LITH_HARBOR_IDLE -> tickLithHarborIdle(session, observer, nowMs);
        }
        session.lastObservedMapId = observer.getMapId();
    }

    private static void observeMilestones(AgentObserverSession session,
                                          Character observer,
                                          Character watched,
                                          long nowMs) {
        if (!session.bariCompleted
                && watched.getQuestStatus(AgentObserverPolicy.BARI_TEST_QUEST_ID)
                == QuestStatus.Status.COMPLETED.getId()) {
            session.bariCompleted = true;
            if (AgentObserverPolicy.ISOLATED_TRAINING_MAP_SET.contains(observer.getMapId())) {
                MOVEMENT.warp(session, observer, AgentObserverPolicy.MAI_MAP_ID);
            }
            if (session.stage == AgentObserverSession.Stage.INVESTIGATING) {
                session.resumeStage = AgentObserverSession.Stage.SHADOWING;
            } else {
                session.stage = AgentObserverSession.Stage.SHADOWING;
                session.setDestination(watched.getMapId());
            }
            log.info("Observer began Bari-to-Southperry shadowing watched={}", watched.getName());
        }
        boolean readyForHandoff = session.bariCompleted
                && watched.getMapId() == AgentObserverPolicy.SOUTHPERRY_MAP_ID
                && watched.getQuestStatus(AgentObserverPolicy.BIGGS_QUEST_ID)
                == QuestStatus.Status.STARTED.getId()
                && watched.getChair() == AgentObserverPolicy.RELAXER_ITEM_ID;
        if (!session.handoffTriggered && readyForHandoff) {
            AgentMapleIslandLithHandoffRuntime.AssignmentResult result =
                    AgentMapleIslandLithHandoffRuntime.requestAll(observer, nowMs);
            if (!result.authorized()) {
                log.warn("Observer Relaxer trigger could not run Shanks administration; "
                        + "observer {} lacks Agent authority", observer.getName());
                return;
            }
            session.handoffTriggered = true;
            session.stage = AgentObserverSession.Stage.LITH_HARBOR_IDLE;
            session.setDestination(AgentObserverPolicy.LITH_HARBOR_MAP_ID);
            MOVEMENT.warp(session, observer, AgentObserverPolicy.LITH_HARBOR_MAP_ID);
            session.nextDecisionAtMs = nowMs + AgentObserverPolicy.lithIdleDelayMs();
            log.info("Relaxer triggered Shanks administration assigned={} queued={} observer={}",
                    result.assigned(), result.alreadyQueued(), observer.getName());
        }
    }

    private static void tickStationed(AgentObserverSession session,
                                      Character observer,
                                      Character watched,
                                      long nowMs) {
        if (observer.getMapId() != AgentObserverPolicy.STATION_MAP_ID) {
            MOVEMENT.warp(session, observer, AgentObserverPolicy.STATION_MAP_ID);
        }
        if (AgentObserverPolicy.watchedReachedRoamingRoute(watched.getMapId())) {
            session.beginApproachRoute();
            return;
        }
        if (session.destinationPoint == null) {
            session.destinationPoint = observationPoint(session, observer);
        }
        MOVEMENT.approach(session, observer, session.destinationPoint, nowMs);
    }

    private static void tickApproachRoam(AgentObserverSession session,
                                         Character observer,
                                         long nowMs) {
        if (!arriveAndIdle(session, observer, nowMs, AgentObserverPolicy.idleDelayMs())) {
            return;
        }
        if (session.nextDecisionAtMs > nowMs) {
            return;
        }
        if (session.approachIndex + 1 >= AgentObserverPolicy.APPROACH_ROUTE.size()) {
            session.stage = AgentObserverSession.Stage.MAPLE_CYCLE;
            session.cycleIndex = 0;
            session.destinationMapId = AgentObserverPolicy.GREEN_SNAIL_MAP_ID;
            session.nextDecisionAtMs = nowMs + AgentObserverPolicy.idleDelayMs();
            return;
        }
        session.approachIndex++;
        session.setDestination(AgentObserverPolicy.APPROACH_ROUTE.get(session.approachIndex));
    }

    private static void tickMapleCycle(AgentObserverSession session,
                                       Character observer,
                                       long nowMs) {
        if (!arriveAndIdle(session, observer, nowMs, AgentObserverPolicy.idleDelayMs())) {
            return;
        }
        if (session.nextDecisionAtMs > nowMs) {
            return;
        }
        if (AgentObserverPolicy.shouldExcursion()) {
            session.resumeMapId = observer.getMapId();
            session.stage = AgentObserverSession.Stage.EXCURSION;
            session.destinationMapId = AgentObserverPolicy.randomTrainingMap();
            MOVEMENT.warp(session, observer, session.destinationMapId);
            session.destinationPoint = observationPoint(session, observer);
            session.nextDecisionAtMs = 0;
            return;
        }
        session.cycleIndex = (session.cycleIndex + 1) % AgentObserverPolicy.ROAM_CYCLE.size();
        session.setDestination(AgentObserverPolicy.ROAM_CYCLE.get(session.cycleIndex));
    }

    private static void tickExcursion(AgentObserverSession session,
                                      Character observer,
                                      long nowMs) {
        if (session.destinationPoint != null) {
            if (!MOVEMENT.approach(session, observer, session.destinationPoint, nowMs)) {
                return;
            }
            session.destinationPoint = null;
            session.nextDecisionAtMs = nowMs + AgentObserverPolicy.excursionDelayMs();
        }
        if (nowMs < session.nextDecisionAtMs) {
            return;
        }
        MOVEMENT.warp(session, observer, session.resumeMapId);
        session.stage = AgentObserverSession.Stage.MAPLE_CYCLE;
        session.destinationMapId = session.resumeMapId;
        session.destinationPoint = observationPoint(session, observer);
        session.nextDecisionAtMs = 0;
    }

    private static void beginInvestigation(AgentObserverSession session,
                                           Character observer,
                                           Character watched,
                                           long nowMs) {
        session.resumeStage = session.stage == AgentObserverSession.Stage.STATIONED
                && AgentObserverPolicy.watchedReachedRoamingRoute(watched.getMapId())
                ? AgentObserverSession.Stage.APPROACH_ROAM
                : session.stage;
        session.resumeMapId = observer.getMapId();
        session.stage = AgentObserverSession.Stage.INVESTIGATING;
        session.investigationStartedAtMs = nowMs;
        session.investigationExpressionShown = false;
        session.nextDecisionAtMs = 0;
    }

    private static void tickInvestigation(AgentObserverSession session,
                                          Character observer,
                                          Character watched,
                                          long nowMs) {
        if (nowMs - session.investigationStartedAtMs
                > AgentObserverPolicy.investigationTimeoutMs()) {
            resumeAfterInvestigation(session, observer, nowMs);
            return;
        }
        if (observer.getMapId() != watched.getMapId()) {
            if (AgentObserverPolicy.ISOLATED_TRAINING_MAP_SET.contains(watched.getMapId())) {
                MOVEMENT.warp(session, observer, watched.getMapId());
            } else {
                if (AgentObserverPolicy.ISOLATED_TRAINING_MAP_SET.contains(observer.getMapId())) {
                    MOVEMENT.warp(session, observer, AgentObserverPolicy.MAI_MAP_ID);
                }
                if (!MOVEMENT.travelNormally(session, observer, watched.getMapId(), nowMs)) {
                    return;
                }
            }
        }
        Point investigatePoint = MOVEMENT.beside(watched, INVESTIGATION_DISTANCE_PX);
        if (!MOVEMENT.approach(session, observer, investigatePoint, nowMs)) {
            return;
        }
        if (!session.investigationExpressionShown) {
            observer.changeFaceExpression(3);
            session.investigationExpressionShown = true;
            session.nextDecisionAtMs = nowMs + AgentObserverPolicy.investigationHoldMs();
            session.investigationRequestedAtMs = 0;
        }
        if (nowMs >= session.nextDecisionAtMs) {
            resumeAfterInvestigation(session, observer, nowMs);
        }
    }

    private static void resumeAfterInvestigation(AgentObserverSession session,
                                                 Character observer,
                                                 long nowMs) {
        session.investigationRequestedAtMs = 0;
        session.investigationExpressionShown = false;
        session.stage = session.bariCompleted
                ? AgentObserverSession.Stage.SHADOWING : session.resumeStage;
        if (session.stage == AgentObserverSession.Stage.LITH_HARBOR_IDLE) {
            MOVEMENT.warp(session, observer, AgentObserverPolicy.LITH_HARBOR_MAP_ID);
            session.destinationMapId = AgentObserverPolicy.LITH_HARBOR_MAP_ID;
            session.nextDecisionAtMs = nowMs + AgentObserverPolicy.lithIdleDelayMs();
            return;
        }
        if (!session.bariCompleted
                && session.resumeStage == AgentObserverSession.Stage.APPROACH_ROAM
                && resumeApproachFromCurrentMap(session, observer, nowMs)) {
            return;
        }
        if (AgentObserverPolicy.ISOLATED_TRAINING_MAP_SET.contains(observer.getMapId())) {
            MOVEMENT.warp(session, observer, session.resumeMapId);
        }
        session.destinationMapId = session.resumeMapId;
        session.destinationPoint = null;
        session.nextDecisionAtMs = nowMs + AgentObserverPolicy.idleDelayMs();
    }

    private static boolean resumeApproachFromCurrentMap(AgentObserverSession session,
                                                        Character observer,
                                                        long nowMs) {
        int approachIndex = AgentObserverPolicy.approachIndex(observer.getMapId());
        if (approachIndex >= 0) {
            if (observer.getMapId() == AgentObserverPolicy.GREEN_SNAIL_MAP_ID) {
                session.stage = AgentObserverSession.Stage.MAPLE_CYCLE;
                session.cycleIndex = 0;
            } else {
                session.stage = AgentObserverSession.Stage.APPROACH_ROAM;
                session.approachIndex = approachIndex;
            }
        } else {
            int cycleIndex = AgentObserverPolicy.cycleIndex(observer.getMapId());
            if (cycleIndex < 0) {
                return false;
            }
            session.stage = AgentObserverSession.Stage.MAPLE_CYCLE;
            session.cycleIndex = cycleIndex;
        }
        session.destinationMapId = observer.getMapId();
        session.destinationPoint = observationPoint(session, observer);
        session.nextDecisionAtMs = 0;
        return true;
    }

    private static void tickShadowing(AgentObserverSession session,
                                      Character observer,
                                      Character watched,
                                      long nowMs) {
        if (observer.getMapId() != watched.getMapId()) {
            session.destinationMapId = watched.getMapId();
            session.destinationPoint = null;
            MOVEMENT.travelNormally(session, observer, watched.getMapId(), nowMs);
            return;
        }
        Point shadowPoint = MOVEMENT.beside(watched, SHADOW_DISTANCE_PX);
        MOVEMENT.approach(session, observer, shadowPoint, nowMs);
    }

    private static void tickLithHarborIdle(AgentObserverSession session,
                                           Character observer,
                                           long nowMs) {
        if (observer.getMapId() != AgentObserverPolicy.LITH_HARBOR_MAP_ID) {
            MOVEMENT.warp(session, observer, AgentObserverPolicy.LITH_HARBOR_MAP_ID);
            session.destinationPoint = null;
        }
        if (session.destinationPoint != null
                && !MOVEMENT.approach(session, observer, session.destinationPoint, nowMs)) {
            return;
        }
        if (nowMs < session.nextDecisionAtMs) {
            return;
        }
        session.destinationPoint = randomLithHarborSpot();
        session.nextDecisionAtMs = nowMs + AgentObserverPolicy.lithIdleDelayMs();
    }

    private static boolean arriveAndIdle(AgentObserverSession session,
                                         Character observer,
                                         long nowMs,
                                         int idleDelayMs) {
        if (observer.getMapId() != session.destinationMapId) {
            MOVEMENT.travelNormally(session, observer, session.destinationMapId, nowMs);
            return false;
        }
        if (session.destinationPoint == null && session.nextDecisionAtMs == 0) {
            session.destinationPoint = observationPoint(session, observer);
        }
        if (session.destinationPoint != null) {
            if (!MOVEMENT.approach(session, observer, session.destinationPoint, nowMs)) {
                return false;
            }
            session.destinationPoint = null;
            session.nextDecisionAtMs = nowMs + idleDelayMs;
        }
        return true;
    }

    private static Point observationPoint(AgentObserverSession session, Character observer) {
        return MOVEMENT.observationPoint(
                observer, session.nextObservationVisit(observer.getMapId()));
    }

    private static Point randomLithHarborSpot() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(AgentObserverPolicy.LITH_HARBOR_MAP_ID);
        List<Point> spots = new ArrayList<>(profile.roamFallbackPoints());
        spots.addAll(profile.restPoints());
        return spots.get(ThreadLocalRandom.current().nextInt(spots.size()));
    }

    private static Character online(int characterId) {
        return AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }

    private static String characterStatus(Character character) {
        return character == null ? "offline" : character.getName() + "@" + character.getMapId();
    }

    private static void stopLocked() {
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }
        if (active != null) {
            AgentObserverSession stopped = active;
            Character observer = online(stopped.observerId);
            MOVEMENT.stop(stopped);
            active = null;
            disconnectLoadedObserver(observer);
        }
    }

    private static void disconnectLoadedObserver(Character observer) {
        if (observer != null && observer.getClient() != null) {
            observer.getClient().forceDisconnect();
        }
    }

    private static int tuningInt(String key) {
        return config.AgentTuning.intValue(TUNING_PREFIX + key);
    }

    public record StartResult(boolean started, String message) {
    }
}
