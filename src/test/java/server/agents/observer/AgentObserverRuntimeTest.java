package server.agents.observer;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGateway;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.CharacterGateway;
import server.agents.integration.MapGateway;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentObserverRuntimeTest {
    @Test
    void loadsNamedOfflineObserverToWatchTheCommandIssuer() throws Exception {
        Character watched = mock(Character.class);
        Character observer = mock(Character.class);
        CharacterGateway characters = mock(CharacterGateway.class);
        AgentClientGateway clients = mock(AgentClientGateway.class);
        AgentPersistenceGateway persistence = mock(AgentPersistenceGateway.class);
        MapGateway maps = mock(MapGateway.class);
        MapleMap stationMap = mock(MapleMap.class);
        Portal stationPortal = mock(Portal.class);
        ScheduledFuture<?> schedule = mock(ScheduledFuture.class);
        Point stationPosition = new Point(15, 25);

        when(watched.getId()).thenReturn(10);
        when(watched.getName()).thenReturn("ControlledAgent");
        when(observer.getId()).thenReturn(20);
        when(observer.getName()).thenReturn("Kiwi");
        when(observer.getMapId()).thenReturn(AgentObserverPolicy.STATION_MAP_ID);
        when(clients.world(watched)).thenReturn(0);
        when(clients.channel(watched)).thenReturn(1);
        when(persistence.findCharacterByName("Kiwi"))
                .thenReturn(new AgentResolvedCharacter(20, "Kiwi", 30, null));
        when(characters.findOnlineCharacterById(20)).thenReturn(null, observer);
        when(maps.resolveMap(0, 1, AgentObserverPolicy.STATION_MAP_ID))
                .thenReturn(stationMap);
        when(stationMap.getPortal(0)).thenReturn(stationPortal);
        when(stationPortal.getPosition()).thenReturn(stationPosition);

        try (MockedStatic<AgentCharacterGatewayRuntime> characterRuntime =
                     mockStatic(AgentCharacterGatewayRuntime.class);
             MockedStatic<AgentClientGatewayRuntime> clientRuntime =
                     mockStatic(AgentClientGatewayRuntime.class);
             MockedStatic<AgentPersistenceGatewayRuntime> persistenceRuntime =
                     mockStatic(AgentPersistenceGatewayRuntime.class);
             MockedStatic<AgentMapGatewayRuntime> mapRuntime =
                     mockStatic(AgentMapGatewayRuntime.class);
             MockedStatic<CosmicAgentOfflineLoader> loader =
                     mockStatic(CosmicAgentOfflineLoader.class);
             MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentMovementCommandRuntime> movement =
                     mockStatic(AgentMovementCommandRuntime.class)) {
            characterRuntime.when(AgentCharacterGatewayRuntime::characters)
                    .thenReturn(characters);
            clientRuntime.when(AgentClientGatewayRuntime::clients).thenReturn(clients);
            persistenceRuntime.when(AgentPersistenceGatewayRuntime::persistence)
                    .thenReturn(persistence);
            mapRuntime.when(AgentMapGatewayRuntime::map).thenReturn(maps);
            loader.when(() -> CosmicAgentOfflineLoader.loadOfflineAgent(
                            20, 0, 1, stationMap, stationPosition))
                    .thenReturn(observer);
            scheduler.when(() -> AgentSchedulerRuntime.register(
                            any(Runnable.class), anyLong()))
                    .thenReturn(schedule);

            AgentObserverRuntime.StartResult result =
                    AgentObserverRuntime.start(watched, "Kiwi", 100L);

            try {
                assertTrue(result.started());
                loader.verify(() -> CosmicAgentOfflineLoader.loadOfflineAgent(
                        20, 0, 1, stationMap, stationPosition));
                scheduler.verify(() -> AgentSchedulerRuntime.register(
                        any(Runnable.class), anyLong()));
            } finally {
                AgentObserverRuntime.stop();
            }
            verify(characters).disconnect(observer, false, false);
        }
    }
}
