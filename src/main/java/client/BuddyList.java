/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client;

import net.packet.Packet;
import net.server.PlayerStorage;
import tools.DatabaseConnection;
import tools.PacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BuddyList {
    public enum BuddyOperation {
        ADDED, DELETED
    }

    public enum BuddyAddResult {
        BUDDYLIST_FULL, ALREADY_ON_LIST, OK
    }

    private final Map<Integer, BuddylistEntry> buddies = new LinkedHashMap<>();
    private int capacity;
    private final Deque<CharacterNameAndId> pendingRequests = new LinkedList<>();
    private Runnable persistenceDirtyMarker = () -> { };

    public BuddyList(int capacity) {
        this.capacity = capacity;
    }

    public boolean contains(int characterId) {
        synchronized (buddies) {
            return buddies.containsKey(characterId);
        }
    }

    public boolean containsVisible(int characterId) {
        BuddylistEntry ble;
        synchronized (buddies) {
            ble = buddies.get(characterId);
        }

        if (ble == null) {
            return false;
        }
        return ble.isVisible();

    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        if (this.capacity != capacity) {
            this.capacity = capacity;
            persistenceDirtyMarker.run();
        }
    }

    public BuddylistEntry get(int characterId) {
        synchronized (buddies) {
            return buddies.get(characterId);
        }
    }

    public BuddylistEntry get(String characterName) {
        String lowerCaseName = characterName.toLowerCase();
        for (BuddylistEntry ble : getBuddies()) {
            if (ble.getName().toLowerCase().equals(lowerCaseName)) {
                return ble;
            }
        }

        return null;
    }

    public void put(BuddylistEntry entry) {
        synchronized (buddies) {
            entry.setPersistenceDirtyMarker(persistenceDirtyMarker);
            BuddylistEntry previous = buddies.put(entry.getCharacterId(), entry);
            if (previous != entry) {
                if (previous != null) {
                    previous.setPersistenceDirtyMarker(() -> {});
                }
                persistenceDirtyMarker.run();
            }
        }
    }

    public void remove(int characterId) {
        synchronized (buddies) {
            BuddylistEntry removed = buddies.remove(characterId);
            if (removed != null) {
                removed.setPersistenceDirtyMarker(() -> {});
                persistenceDirtyMarker.run();
            }
        }
    }

    void setPersistenceDirtyMarker(Runnable persistenceDirtyMarker) {
        synchronized (buddies) {
            this.persistenceDirtyMarker = Objects.requireNonNull(persistenceDirtyMarker);
            for (BuddylistEntry entry : buddies.values()) {
                entry.setPersistenceDirtyMarker(persistenceDirtyMarker);
            }
        }
    }

    public Collection<BuddylistEntry> getBuddies() {
        synchronized (buddies) {
            return Collections.unmodifiableList(new ArrayList<>(buddies.values()));
        }
    }

    public PersistenceSnapshot persistenceSnapshot() {
        synchronized (buddies) {
            List<PersistedEntry> entries = new ArrayList<>(buddies.size());
            for (BuddylistEntry entry : buddies.values()) {
                entries.add(new PersistedEntry(entry.getName(), entry.getGroup(),
                        entry.getCharacterId(), entry.isVisible()));
            }
            return new PersistenceSnapshot(capacity, entries);
        }
    }

    static BuddyList fromPersistenceSnapshot(PersistenceSnapshot snapshot) {
        BuddyList restored = new BuddyList(snapshot.capacity());
        for (PersistedEntry entry : snapshot.entries()) {
            restored.put(new BuddylistEntry(entry.name(), entry.group(), entry.characterId(), -1, entry.visible()));
        }
        return restored;
    }

    public record PersistedEntry(String name, String group, int characterId, boolean visible) {
    }

    public record PersistenceSnapshot(int capacity, List<PersistedEntry> entries) {
        public PersistenceSnapshot {
            entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    public boolean isFull() {
        synchronized (buddies) {
            return buddies.size() >= capacity;
        }
    }

    public int[] getBuddyIds() {
        synchronized (buddies) {
            int[] buddyIds = new int[buddies.size()];
            int i = 0;
            for (BuddylistEntry ble : buddies.values()) {
                buddyIds[i++] = ble.getCharacterId();
            }
            return buddyIds;
        }
    }

    public void broadcast(Packet packet, PlayerStorage pstorage) {
        for (int bid : getBuddyIds()) {
            Character chr = pstorage.getCharacterById(bid);

            if (chr != null && chr.isLoggedinWorld()) {
                chr.sendPacket(packet);
            }
        }
    }

    public void loadFromDb(int characterId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT b.buddyid, b.pending, b.group, c.name as buddyname FROM buddies as b, characters as c WHERE c.id = b.buddyid AND b.characterid = ?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getInt("pending") == 1) {
                            pendingRequests.push(new CharacterNameAndId(rs.getInt("buddyid"), rs.getString("buddyname")));
                        } else {
                            put(new BuddylistEntry(rs.getString("buddyname"), rs.getString("group"), rs.getInt("buddyid"), (byte) -1, true));
                        }
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM buddies WHERE pending = 1 AND characterid = ?")) {
                ps.setInt(1, characterId);
                ps.executeUpdate();
            }

            // Show registered bots as always-online so the buddy list invite option is available
            try (PreparedStatement ps = con.prepareStatement("SELECT bot_char_id FROM bot_owners WHERE owner_char_id = ?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BuddylistEntry entry = get(rs.getInt("bot_char_id"));
                        if (entry != null) {
                            entry.setChannel(1);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            monitoring.RuntimeFailureLogger.log(ex);
        }
    }

    public CharacterNameAndId pollPendingRequest() {
        return pendingRequests.pollLast();
    }

    public void addBuddyRequest(Client c, int cidFrom, String nameFrom, int channelFrom) {
        put(new BuddylistEntry(nameFrom, "Default Group", cidFrom, channelFrom, false));
        if (pendingRequests.isEmpty()) {
            c.sendPacket(PacketCreator.requestBuddylistAdd(cidFrom, c.getPlayer().getId(), nameFrom));
        } else {
            pendingRequests.push(new CharacterNameAndId(cidFrom, nameFrom));
        }
    }
}
