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
package client.inventory;

import client.Character;
import client.inventory.manipulator.CashIdGenerator;
import constants.game.ExpTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovement;
import server.movement.LifeMovementFragment;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.Pair;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * @author Matze
 */
public class Pet extends Item {
    private static final Logger log = LoggerFactory.getLogger(Pet.class);
    private String name;
    private int uniqueid;
    private int tameness = 0;
    private byte level = 1;
    private int fullness = 100;
    private int Fh;
    private Point pos;
    private int stance;
    private boolean summoned;
    private int petAttribute = 0;

    public enum PetAttribute {
        OWNER_SPEED(0x01);

        private final int i;

        PetAttribute(int i) {
            this.i = i;
        }

        public int getValue() {
            return i;
        }
    }

    private Pet(int id, short position, int uniqueid) {
        super(id, position, (short) 1);
        this.uniqueid = uniqueid;
        this.pos = new Point(0, 0);
    }

    public static Pet loadFromDb(int itemid, short position, int petid) {
        Pet ret = new Pet(itemid, position, petid);
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT name, level, closeness, fullness, summoned, flag FROM pets WHERE petid = ?")) { // Get the pet details...
            ps.setInt(1, petid);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                ret.setName(rs.getString("name"));
                ret.setTameness(Math.min(rs.getInt("closeness"), 30000));
                ret.setLevel((byte) Math.min(rs.getByte("level"), 30));
                ret.setFullness(Math.min(rs.getInt("fullness"), 100));
                ret.setSummoned(rs.getInt("summoned") == 1);
                ret.setPetAttribute(rs.getInt("flag"));
            }
            return ret;
        } catch (SQLException e) {
            monitoring.RuntimeFailureLogger.log(e);
            return null;
        }
    }

    public static void deleteFromDb(Character owner, int petid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteFromDbTransaction(con, petid);
            owner.resetExcluded(petid);
            CashIdGenerator.freeCashId(petid);
        } catch (SQLException ex) {
            log.error("Failed to delete pet petId={}", petid, ex);
        }
    }

    static void deleteFromDbTransaction(Connection con, int petid) throws SQLException {
        con.setAutoCommit(false);
        try {
            deleteFromDb(con, petid);
            con.commit();
        } catch (SQLException | RuntimeException failure) {
            try {
                con.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public static void deleteFromDb(Connection con, int petid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM petignores WHERE petid = ?")) {
            ps.setInt(1, petid);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM pets WHERE petid = ?")) {
            ps.setInt(1, petid);
            ps.executeUpdate();
        }
    }

    public void saveToDb() {
        try (Connection con = DatabaseConnection.getConnection()) {
            saveToDb(con);
        } catch (SQLException e) {
            log.error("Failed to save pet petId={}", getUniqueId(), e);
        }
    }

    public void saveToDb(Connection con) throws SQLException {
        persistenceSnapshot().saveToDb(con);
    }

    public PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(getName(), getLevel(), getTameness(), getFullness(), isSummoned(),
                getPetAttribute(), getUniqueId());
    }

    public record PersistenceSnapshot(String name, int level, int tameness, int fullness, boolean summoned,
                                      int petAttribute, int uniqueId) {
        public Pet restore(int itemId, short position) {
            Pet restored = new Pet(itemId, position, uniqueId);
            restored.name = name;
            restored.level = (byte) level;
            restored.tameness = tameness;
            restored.fullness = fullness;
            restored.summoned = summoned;
            restored.petAttribute = petAttribute;
            return restored;
        }

        public void saveToDb(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE pets SET name = ?, level = ?, closeness = ?, fullness = ?, summoned = ?, flag = ? WHERE petid = ?")) {
            ps.setString(1, name);
            ps.setInt(2, level);
            ps.setInt(3, tameness);
            ps.setInt(4, fullness);
            ps.setInt(5, summoned ? 1 : 0);
            ps.setInt(6, petAttribute);
            ps.setInt(7, uniqueId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Pet row not found for petId " + uniqueId);
            }
        }
        }
    }

    public static int createPet(int itemid) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO pets (petid, name, level, closeness, fullness, summoned, flag) VALUES (?, ?, 1, 0, 100, 0, 0)")) {
            int ret = CashIdGenerator.generateCashId();
            ps.setInt(1, ret);
            ps.setString(2, ItemInformationProvider.getInstance().getName(itemid));
            ps.executeUpdate();
            return ret;
        } catch (SQLException e) {
            monitoring.RuntimeFailureLogger.log(e);
            return -1;
        }
    }

    public static int createPet(int itemid, byte level, int tameness, int fullness) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO pets (petid, name, level, closeness, fullness, summoned, flag) VALUES (?, ?, ?, ?, ?, 0, 0)")) {
            int ret = CashIdGenerator.generateCashId();
            ps.setInt(1, ret);
            ps.setString(2, ItemInformationProvider.getInstance().getName(itemid));
            ps.setByte(3, level);
            ps.setInt(4, tameness);
            ps.setInt(5, fullness);
            ps.executeUpdate();
            return ret;
        } catch (SQLException e) {
            monitoring.RuntimeFailureLogger.log(e);
            return -1;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!Objects.equals(this.name, name)) {
            this.name = name;
            markPersistenceDirty();
        }
    }

    public int getUniqueId() {
        return uniqueid;
    }

    public int getTameness() {
        return tameness;
    }

    public void setTameness(int tameness) {
        if (this.tameness != tameness) {
            this.tameness = tameness;
            markPersistenceDirty();
        }
    }

    public byte getLevel() {
        return level;
    }

    public void gainTamenessFullness(Character owner, int incTameness, int incFullness, int type) {
        gainTamenessFullness(owner, incTameness, incFullness, type, false);
    }

    public void gainTamenessFullness(Character owner, int incTameness, int incFullness, int type, boolean forceEnjoy) {
        byte slot = owner.getPetIndex(this);
        int previousTameness = tameness;
        int previousFullness = fullness;
        byte previousLevel = level;
        boolean enjoyed;

        //will NOT increase pet's tameness if tried to feed pet with 100% fullness
        // unless forceEnjoy == true (cash shop)
        if (fullness < 100 || incFullness == 0 || forceEnjoy) {   //incFullness == 0: command given
            int newFullness = fullness + incFullness;
            if (newFullness > 100) {
                newFullness = 100;
            }
            fullness = newFullness;

            if (incTameness > 0 && tameness < 30000) {
                int newTameness = tameness + incTameness;
                if (newTameness > 30000) {
                    newTameness = 30000;
                }

                tameness = newTameness;
                while (newTameness >= ExpTable.getTamenessNeededForLevel(level)) {
                    level += 1;
                    owner.sendPacket(PacketCreator.showOwnPetLevelUp(slot));
                    owner.getMap().broadcastMessage(PacketCreator.showPetLevelUp(owner, slot));
                }
            }

            enjoyed = true;
        } else {
            int newTameness = tameness - 1;
            if (newTameness < 0) {
                newTameness = 0;
            }

            tameness = newTameness;
            if (level > 1 && newTameness < ExpTable.getTamenessNeededForLevel(level - 1)) {
                level -= 1;
            }

            enjoyed = false;
        }

        if (tameness != previousTameness || fullness != previousFullness || level != previousLevel) {
            owner.markPersistenceDirty(Character.PersistenceSection.PETS);
        }

        owner.getMap().broadcastMessage(PacketCreator.petFoodResponse(owner.getId(), slot, enjoyed, false));
        saveToDb();

        Item petz = owner.getInventory(InventoryType.CASH).getItem(getPosition());
        if (petz != null) {
            owner.forceUpdateItem(petz);
        }
    }

    public void setLevel(byte level) {
        if (this.level != level) {
            this.level = level;
            markPersistenceDirty();
        }
    }

    public int getFullness() {
        return fullness;
    }

    public void setFullness(int fullness) {
        if (this.fullness != fullness) {
            this.fullness = fullness;
            markPersistenceDirty();
        }
    }

    public int getFh() {
        return Fh;
    }

    public void setFh(int Fh) {
        this.Fh = Fh;
    }

    public Point getPos() {
        return pos;
    }

    public void setPos(Point pos) {
        this.pos = pos;
    }

    public int getStance() {
        return stance;
    }

    public void setStance(int stance) {
        this.stance = stance;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean yes) {
        if (this.summoned != yes) {
            this.summoned = yes;
            markPersistenceDirty();
        }
    }

    public int getPetAttribute() {
        return this.petAttribute;
    }

    private void setPetAttribute(int flag) {
        if (this.petAttribute != flag) {
            this.petAttribute = flag;
            markPersistenceDirty();
        }
    }

    public void addPetAttribute(Character owner, PetAttribute flag) {
        int updated = this.petAttribute | flag.getValue();
        if (updated == this.petAttribute) {
            return;
        }
        this.petAttribute = updated;
        owner.markPersistenceDirty(Character.PersistenceSection.PETS);
        saveToDb();

        Item petz = owner.getInventory(InventoryType.CASH).getItem(getPosition());
        if (petz != null) {
            owner.forceUpdateItem(petz);
        }
    }

    public void removePetAttribute(Character owner, PetAttribute flag) {
        int updated = this.petAttribute & (0xFFFFFFFF ^ flag.getValue());
        if (updated == this.petAttribute) {
            return;
        }
        this.petAttribute = updated;
        owner.markPersistenceDirty(Character.PersistenceSection.PETS);
        saveToDb();

        Item petz = owner.getInventory(InventoryType.CASH).getItem(getPosition());
        if (petz != null) {
            owner.forceUpdateItem(petz);
        }
    }

    public Pair<Integer, Boolean> canConsume(int itemId) {
        return ItemInformationProvider.getInstance().canPetConsume(this.getItemId(), itemId);
    }

    public void updatePosition(List<LifeMovementFragment> movement) {
        for (LifeMovementFragment move : movement) {
            if (move instanceof LifeMovement) {
                if (move instanceof AbsoluteLifeMovement) {
                    this.setPos(move.getPosition());
                }
                this.setStance(((LifeMovement) move).getNewstate());
            }
        }
    }
}
