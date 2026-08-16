package server.agents.observation.commerce;

import client.Character;
import client.Client;
import net.server.channel.Channel;
import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.PopulationAdmissionPlanner;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.session.EconomySessionPort;
import server.maps.MapManager;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommerceObservationSessionPortTest {
    @Test
    void materializesOnlyCohortsWhoseAdmissionIsDue() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        LiveAgent initial = liveAgent(101, 100000000, new Point(10, 20));
        LiveAgent future = liveAgent(102, 102000000, new Point(30, 40));
        CommerceParticipant first = participant("agent-1");
        CommerceParticipant second = participant("agent-2");
        EconomySessionPort delegate = mock(EconomySessionPort.class);
        when(delegate.requestEntry(any(), any(), any())).thenReturn(
                EconomySessionPort.EntryResult.accepted(
                        UUID.randomUUID(), start.plus(Duration.ofMinutes(30)), "accepted"));

        CommerceObservationSessionPort sessions = new CommerceObservationSessionPort(
                delegate, Map.of("agent-1", initial.character(), "agent-2", future.character()),
                List.of(new PopulationAdmissionPlanner.Admission(first, start),
                        new PopulationAdmissionPlanner.Admission(second, start.plus(Duration.ofDays(1)))),
                start, 910000000);

        assertEquals(1, sessions.stagedCount());
        verify(initial.character()).changeMap(initial.entrance(), initial.spawnPosition());
        verify(future.character(), never()).changeMap(future.entrance(), future.spawnPosition());

        sessions.requestEntry(second, entryRequest(), start.plus(Duration.ofDays(1)));

        assertEquals(0, sessions.stagedCount());
        verify(future.character()).changeMap(future.entrance(), future.spawnPosition());
    }

    @Test
    void restoresStillUnadmittedCharactersWhenObservationStops() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Point originalPosition = new Point(30, 40);
        LiveAgent future = liveAgent(102, 102000000, originalPosition);
        CommerceParticipant profile = participant("agent-1");
        CommerceObservationSessionPort sessions = new CommerceObservationSessionPort(
                mock(EconomySessionPort.class), Map.of("agent-1", future.character()),
                List.of(new PopulationAdmissionPlanner.Admission(
                        profile, start.plus(Duration.ofDays(1)))), start, 910000000);

        sessions.restoreUnadmittedCharacters();

        assertEquals(0, sessions.stagedCount());
        verify(future.character()).changeMap(future.original(), originalPosition);
    }

    private static EconomySessionPort.EntryRequest entryRequest() {
        return new EconomySessionPort.EntryRequest(UUID.randomUUID(), "observation",
                Duration.ofMinutes(30), Duration.ofMinutes(5), Map.of());
    }

    private static CommerceParticipant participant(String id) {
        return new CommerceParticipant(id, "warrior", .5, .5, .5, .5,
                .5, .5, 24, .5, .5);
    }

    private static LiveAgent liveAgent(int id, int mapId, Point position) {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        Channel channel = mock(Channel.class);
        MapManager maps = mock(MapManager.class);
        MapleMap original = mock(MapleMap.class);
        MapleMap entrance = mock(MapleMap.class);
        Portal spawn = mock(Portal.class);
        Point spawnPosition = new Point(0, 0);
        when(character.getId()).thenReturn(id);
        when(character.getClient()).thenReturn(client);
        when(character.getMap()).thenReturn(original);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(new Point(position));
        when(client.getChannelServer()).thenReturn(channel);
        when(channel.getMapFactory()).thenReturn(maps);
        when(maps.getMap(910000000)).thenReturn(entrance);
        when(maps.getMap(mapId)).thenReturn(original);
        when(entrance.getPortal(0)).thenReturn(spawn);
        when(spawn.getPosition()).thenReturn(spawnPosition);
        return new LiveAgent(character, original, entrance, spawnPosition);
    }

    private record LiveAgent(Character character, MapleMap original,
                             MapleMap entrance, Point spawnPosition) {
    }
}
