package net.server.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartySnapshotTest {
    @Test
    void eligibleMembersAreCopiedOnSetAndGet() {
        PartyCharacter leader = new PartyCharacter();
        Party party = new Party(1, leader);
        List<PartyCharacter> source = new ArrayList<>(List.of(leader));
        party.setEligibleMembers(source);
        Collection<PartyCharacter> snapshot = party.getEligibleMembers();

        source.clear();
        party.setEligibleMembers(List.of());

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }
}
