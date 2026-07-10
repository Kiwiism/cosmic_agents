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
package server;

import client.Client;
import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ItemFactory;
import constants.game.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.ref.WeakReference;

/**
 * @author Matze
 */
public class Storage {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    private static final Map<Integer, Integer> trunkGetCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> trunkPutCache = new ConcurrentHashMap<>();

    private final int id;
    private int currentNpcid;
    private int meso;
    private byte slots;
    private final Map<InventoryType, List<Item>> typeItems = new HashMap<>();
    private List<Item> items = new LinkedList<>();
    private final Lock lock = new ReentrantLock(true);
    private WeakReference<Character> persistenceOwner = new WeakReference<>(null);

    public void attachPersistenceOwner(Character owner) {
        persistenceOwner = new WeakReference<>(owner);
        lock.lock();
        try {
            for (Item item : items) {
                attachPersistenceOwner(item);
            }
        } finally {
            lock.unlock();
        }
    }

    private void markPersistenceDirty() {
        Character owner = persistenceOwner.get();
        if (owner != null) {
            owner.setUsedStorage();
        }
    }

    private Storage(int id, byte slots, int meso) {
        this.id = id;
        this.slots = slots;
        this.meso = meso;
    }

    private static Storage create(int id, int world) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO storages (accountid, world, slots, meso) VALUES (?, ?, 4, 0)")) {
            ps.setInt(1, id);
            ps.setInt(2, world);
            ps.executeUpdate();
        }

        return loadOrCreateFromDB(id, world);
    }

    public static Storage loadOrCreateFromDB(int id, int world) {
        Storage ret;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT storageid, slots, meso FROM storages WHERE accountid = ? AND world = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, world);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ret = new Storage(rs.getInt("storageid"), (byte) rs.getInt("slots"), rs.getInt("meso"));
                    for (Pair<Item, InventoryType> item : ItemFactory.STORAGE.loadItems(ret.id, false)) {
                        ret.items.add(item.getLeft());
                    }
                } else {
                    ret = create(id, world);
                }
            }

            return ret;
        } catch (SQLException ex) { // exceptions leading to deploy null storages found thanks to Jefe
            log.error("SQL error occurred when trying to load storage for accId {}, world {}", id, GameConstants.WORLD_NAMES[world], ex);
            throw new RuntimeException(ex);
        }
    }

    public byte getSlots() {
        return slots;
    }

    public boolean canGainSlots(int slots) {
        slots += this.slots;
        return slots <= 48;
    }

    public boolean gainSlots(int slots) {
        lock.lock();
        try {
            if (canGainSlots(slots)) {
                slots += this.slots;
                this.slots = (byte) slots;
                markPersistenceDirty();
                return true;
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    public void saveToDB(Connection con) throws SQLException {
        PersistenceSnapshot snapshot = persistenceSnapshot();
        try (PreparedStatement ps = con.prepareStatement("UPDATE storages SET slots = ?, meso = ? WHERE storageid = ?")) {
            ps.setInt(1, snapshot.slots());
            ps.setInt(2, snapshot.meso());
            ps.setInt(3, snapshot.id());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Storage row not found for storageId " + snapshot.id());
            }
        }
        ItemFactory.STORAGE.saveItems(snapshot.items(), snapshot.id(), con);
    }

    public PersistenceSnapshot persistenceSnapshot() {
        lock.lock();
        try {
            List<Pair<Item, InventoryType>> itemSnapshot = new ArrayList<>(items.size());
            for (Item item : items) {
                itemSnapshot.add(new Pair<>(item.copy(), item.getInventoryType()));
            }
            return new PersistenceSnapshot(id, slots, meso, itemSnapshot);
        } finally {
            lock.unlock();
        }
    }

    static Storage fromPersistenceSnapshot(PersistenceSnapshot snapshot) {
        Storage restored = new Storage(snapshot.id(), snapshot.slots(), snapshot.meso());
        for (Pair<Item, InventoryType> item : snapshot.items()) {
            restored.items.add(item.getLeft().copy());
        }
        return restored;
    }

    public record PersistenceSnapshot(int id, byte slots, int meso,
                                      List<Pair<Item, InventoryType>> items) {
        public PersistenceSnapshot {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public Item getItem(byte slot) {
        lock.lock();
        try {
            return items.get(slot);
        } finally {
            lock.unlock();
        }
    }

    public boolean takeOut(Item item) {
        lock.lock();
        try {
            boolean ret = items.remove(item);

            if (ret) {
                item.setPersistenceDirtyMarker(() -> { });
            }

            InventoryType type = item.getInventoryType();
            typeItems.put(type, new ArrayList<>(filterItems(type)));

            if (ret) {
                markPersistenceDirty();
            }

            return ret;
        } finally {
            lock.unlock();
        }
    }

    public boolean store(Item item) {
        lock.lock();
        try {
            if (isFull()) { // thanks Optimist for noticing unrestricted amount of insertions here
                return false;
            }

            attachPersistenceOwner(item);
            items.add(item);

            InventoryType type = item.getInventoryType();
            typeItems.put(type, new ArrayList<>(filterItems(type)));

            markPersistenceDirty();

            return true;
        } finally {
            lock.unlock();
        }
    }

    public void upsertFromDatabaseConsole(Short sourcePosition, Integer expectedItemId, Item replacement) {
        lock.lock();
        try {
            short targetPosition = replacement.getPosition();
            if (targetPosition < 1 || targetPosition > slots) {
                throw new IllegalArgumentException("Storage slot is outside the available range");
            }

            Item source = sourcePosition == null ? null : findByPosition(sourcePosition);
            if (sourcePosition != null && (source == null || expectedItemId != null && source.getItemId() != expectedItemId)) {
                throw new IllegalStateException("Storage item changed before the edit was applied");
            }
            Item target = findByPosition(targetPosition);
            if (target != null && target != source) {
                throw new IllegalStateException("Target storage slot is occupied");
            }
            if (source == null && items.size() >= slots) {
                throw new IllegalStateException("Storage is full");
            }
            if (source != null && source.getPetId() > -1) {
                throw new IllegalStateException("Linked pet items cannot be edited while online");
            }
            if (source instanceof Equip sourceEquip && sourceEquip.getRingId() > -1) {
                if (!(replacement instanceof Equip replacementEquip)
                        || replacement.getItemId() != source.getItemId()) {
                    throw new IllegalStateException("Linked ring equipment cannot be replaced while online");
                }
                replacementEquip.setRingId(sourceEquip.getRingId());
            }

            if (source != null) {
                items.remove(source);
                source.setPersistenceDirtyMarker(() -> { });
            }
            attachPersistenceOwner(replacement);
            items.add(replacement);
            rebuildTypeItems();
            markPersistenceDirty();
        } finally {
            lock.unlock();
        }
    }

    public Item deleteFromDatabaseConsole(short position, int expectedItemId) {
        lock.lock();
        try {
            Item item = findByPosition(position);
            if (item == null || item.getItemId() != expectedItemId) {
                throw new IllegalStateException("Storage item changed before the delete was applied");
            }
            items.remove(item);
            item.setPersistenceDirtyMarker(() -> { });
            rebuildTypeItems();
            markPersistenceDirty();
            return item;
        } finally {
            lock.unlock();
        }
    }

    public void swapFromDatabaseConsole(short firstPosition, int firstItemId,
                                        short secondPosition, int secondItemId) {
        lock.lock();
        try {
            Item first = findByPosition(firstPosition);
            Item second = findByPosition(secondPosition);
            if (first == null || first.getItemId() != firstItemId
                    || second == null || second.getItemId() != secondItemId) {
                throw new IllegalStateException("Storage items changed before the swap was applied");
            }
            first.setPosition(secondPosition);
            second.setPosition(firstPosition);
            rebuildTypeItems();
            markPersistenceDirty();
        } finally {
            lock.unlock();
        }
    }

    public void updateFromDatabaseConsole(int newSlots, int newMeso) {
        lock.lock();
        try {
            if (newSlots < 4 || newSlots > 48 || newMeso < 0) {
                throw new IllegalArgumentException("Invalid storage slots or meso value");
            }
            if (items.size() > newSlots || items.stream().anyMatch(item -> item.getPosition() > newSlots)) {
                throw new IllegalStateException("Storage contains items outside the requested slot limit");
            }
            slots = (byte) newSlots;
            meso = newMeso;
            markPersistenceDirty();
        } finally {
            lock.unlock();
        }
    }

    public void refreshDatabaseConsoleView(Client c) {
        lock.lock();
        try {
            if (currentNpcid > 0) {
                c.sendPacket(PacketCreator.getStorage(currentNpcid, slots, getItems(), meso));
            }
        } finally {
            lock.unlock();
        }
    }

    private Item findByPosition(short position) {
        for (Item item : items) {
            if (item.getPosition() == position) {
                return item;
            }
        }
        return null;
    }

    private void rebuildTypeItems() {
        for (InventoryType type : InventoryType.values()) {
            typeItems.put(type, new ArrayList<>(filterItems(type)));
        }
    }

    private void attachPersistenceOwner(Item item) {
        item.setPersistenceDirtyMarker(this::markPersistenceDirty);
    }

    public List<Item> getItems() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(items));
        } finally {
            lock.unlock();
        }
    }

    private List<Item> filterItems(InventoryType type) {
        List<Item> storageItems = getItems();
        List<Item> ret = new LinkedList<>();

        for (Item item : storageItems) {
            if (item.getInventoryType() == type) {
                ret.add(item);
            }
        }
        return ret;
    }

    public byte getSlot(InventoryType type, byte slot) {
        lock.lock();
        try {
            byte ret = 0;
            List<Item> storageItems = getItems();
            for (Item item : storageItems) {
                if (item == typeItems.get(type).get(slot)) {
                    return ret;
                }
                ret++;
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    public void sendStorage(Client c, int npcId) {
        if (c.getPlayer().getLevel() < 15) {
            c.getPlayer().dropMessage(1, "You may only use the storage once you have reached level 15.");
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        lock.lock();
        try {
            items.sort((o1, o2) -> {
                if (o1.getInventoryType().getType() < o2.getInventoryType().getType()) {
                    return -1;
                } else if (o1.getInventoryType() == o2.getInventoryType()) {
                    return 0;
                }
                return 1;
            });

            List<Item> storageItems = getItems();
            for (InventoryType type : InventoryType.values()) {
                typeItems.put(type, new ArrayList<>(storageItems));
            }

            currentNpcid = npcId;
            c.sendPacket(PacketCreator.getStorage(npcId, slots, storageItems, meso));
        } finally {
            lock.unlock();
        }
    }

    public void sendStored(Client c, InventoryType type) {
        lock.lock();
        try {
            c.sendPacket(PacketCreator.storeStorage(slots, type, typeItems.get(type)));
        } finally {
            lock.unlock();
        }
    }

    public void sendTakenOut(Client c, InventoryType type) {
        lock.lock();
        try {
            c.sendPacket(PacketCreator.takeOutStorage(slots, type, typeItems.get(type)));
        } finally {
            lock.unlock();
        }
    }

    public void arrangeItems(Client c) {
        lock.lock();
        try {
            StorageInventory msi = new StorageInventory(c, items);
            msi.mergeItems();
            items = msi.sortItems();

            for (InventoryType type : InventoryType.values()) {
                typeItems.put(type, new ArrayList<>(items));
            }

            c.sendPacket(PacketCreator.arrangeStorage(slots, items));
            markPersistenceDirty();
        } finally {
            lock.unlock();
        }
    }

    public int getMeso() {
        return meso;
    }

    public void setMeso(int meso) {
        if (meso < 0) {
            throw new RuntimeException();
        }
        if (this.meso != meso) {
            this.meso = meso;
            markPersistenceDirty();
        }
    }

    public void sendMeso(Client c) {
        c.sendPacket(PacketCreator.mesoStorage(slots, meso));
    }

    public int getStoreFee() {  // thanks to GabrielSin
        int npcId = currentNpcid;
        Integer fee = trunkPutCache.get(npcId);
        if (fee == null) {
            fee = 100;

            DataProvider npc = DataProviderFactory.getDataProvider(WZFiles.NPC);
            Data npcData = npc.getData(npcId + ".img");
            if (npcData != null) {
                fee = DataTool.getIntConvert("info/trunkPut", npcData, 100);
            }

            trunkPutCache.put(npcId, fee);
        }

        return fee;
    }

    public int getTakeOutFee() {
        int npcId = currentNpcid;
        Integer fee = trunkGetCache.get(npcId);
        if (fee == null) {
            fee = 0;

            DataProvider npc = DataProviderFactory.getDataProvider(WZFiles.NPC);
            Data npcData = npc.getData(npcId + ".img");
            if (npcData != null) {
                fee = DataTool.getIntConvert("info/trunkGet", npcData, 0);
            }

            trunkGetCache.put(npcId, fee);
        }

        return fee;
    }

    public boolean isFull() {
        lock.lock();
        try {
            return items.size() >= slots;
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            typeItems.clear();
            currentNpcid = 0;
        } finally {
            lock.unlock();
        }
    }

}
