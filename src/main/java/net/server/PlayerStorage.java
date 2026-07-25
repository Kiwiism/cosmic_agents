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
package net.server;

import client.BotClient;
import client.Character;
import client.Client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PlayerStorage {
    private final Map<Integer, Character> storage = new LinkedHashMap<>();
    private final Map<String, Character> nameStorage = new LinkedHashMap<>();
    private final Lock rlock;
    private final Lock wlock;
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public PlayerStorage() {
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
        this.rlock = readWriteLock.readLock();
        this.wlock = readWriteLock.writeLock();
    }

    public void addPlayer(Character chr) {
        wlock.lock();
        try {
            Character previous = storage.put(chr.getId(), chr);
            if (previous != null) {
                nameStorage.remove(previous.getName().toLowerCase());
            }
            nameStorage.put(chr.getName().toLowerCase(), chr);
            snapshot = null;
        } finally {
            wlock.unlock();
        }
    }

    public Character removePlayer(int chr) {
        wlock.lock();
        try {
            Character mc = storage.remove(chr);
            if (mc != null) {
                nameStorage.remove(mc.getName().toLowerCase());
                snapshot = null;
            }

            return mc;
        } finally {
            wlock.unlock();
        }
    }

    public Character getCharacterByName(String name) {
        rlock.lock();
        try {
            return nameStorage.get(name.toLowerCase());
        } finally {
            rlock.unlock();
        }
    }

    public Character getCharacterById(int id) {
        rlock.lock();
        try {
            return storage.get(id);
        } finally {
            rlock.unlock();
        }
    }

    public Collection<Character> getAllCharacters() {
        return currentSnapshot().allCharacters();
    }

    public Collection<Character> getRealPlayers() {
        return currentSnapshot().realPlayers();
    }

    public Collection<Character> getNetworkRecipients() {
        return currentSnapshot().networkRecipients();
    }

    public Collection<Character> getAgents() {
        return currentSnapshot().agents();
    }

    public final void disconnectAll() {
        List<Character> chrList = currentSnapshot().allCharacters();

        for (Character mc : chrList) {
            Client client = mc.getClient();
            if (client != null) {
                client.forceDisconnect();
            }
        }

        wlock.lock();
        try {
            storage.clear();
            nameStorage.clear();
            snapshot = Snapshot.EMPTY;
        } finally {
            wlock.unlock();
        }
    }

    public int getSize() {
        rlock.lock();
        try {
            return storage.size();
        } finally {
            rlock.unlock();
        }
    }

    public int getRealPlayerCount() {
        return currentSnapshot().realPlayers().size();
    }

    public int getAgentCount() {
        return currentSnapshot().agents().size();
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }

        rlock.lock();
        try {
            current = snapshot;
            if (current == null) {
                List<Character> allCharacters = List.copyOf(storage.values());
                List<Character> realPlayers = new ArrayList<>(allCharacters.size());
                List<Character> networkRecipients = new ArrayList<>(allCharacters.size());
                List<Character> agents = new ArrayList<>(allCharacters.size());
                for (Character character : allCharacters) {
                    if (character.getClient() instanceof BotClient) {
                        agents.add(character);
                    } else {
                        realPlayers.add(character);
                        if (character.getClient() != null) {
                            networkRecipients.add(character);
                        }
                    }
                }
                current = new Snapshot(allCharacters, List.copyOf(realPlayers),
                        List.copyOf(networkRecipients), List.copyOf(agents));
                snapshot = current;
            }
            return current;
        } finally {
            rlock.unlock();
        }
    }

    private record Snapshot(
            List<Character> allCharacters,
            List<Character> realPlayers,
            List<Character> networkRecipients,
            List<Character> agents
    ) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), List.of());
    }
}
