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

import java.util.Objects;

/**
 * @author PurpleMadness < Patrick :O >
 */
public class Mount {
    private int itemid;
    private int skillid;
    private int tiredness;
    private int exp;
    private int level;
    private Character owner;
    private boolean active;
    private Runnable persistenceDirtyMarker = () -> {};

    public Mount(Character owner, int id, int skillid) {
        this.itemid = id;
        this.skillid = skillid;
        this.tiredness = 0;
        this.level = 1;
        this.exp = 0;
        this.owner = owner;
        active = true;
    }

    public int getItemId() {
        return itemid;
    }

    public int getSkillId() {
        return skillid;
    }

    /**
     * 1902000 - Hog
     * 1902001 - Silver Mane
     * 1902002 - Red Draco
     * 1902005 - Mimiana
     * 1902006 - Mimio
     * 1902007 - Shinjou
     * 1902008 - Frog
     * 1902009 - Ostrich
     * 1902010 - Frog
     * 1902011 - Turtle
     * 1902012 - Yeti
     *
     * @return the id
     */
    public int getId() {
        if (this.itemid < 1903000) {
            return itemid - 1901999;
        }
        return 5;
    }

    public int getTiredness() {
        return tiredness;
    }

    public int getExp() {
        return exp;
    }

    public int getLevel() {
        return level;
    }

    void setPersistenceDirtyMarker(Runnable persistenceDirtyMarker) {
        this.persistenceDirtyMarker = Objects.requireNonNull(persistenceDirtyMarker);
    }

    PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(exp, level, tiredness);
    }

    static Mount fromPersistenceSnapshot(Character owner, int itemId, int skillId,
                                         PersistenceSnapshot snapshot) {
        Mount mount = new Mount(owner, itemId, skillId);
        mount.exp = snapshot.exp();
        mount.level = snapshot.level();
        mount.tiredness = snapshot.tiredness();
        return mount;
    }

    public void setTiredness(int newtiredness) {
        int clampedTiredness = Math.max(0, newtiredness);
        if (tiredness != clampedTiredness) {
            tiredness = clampedTiredness;
            persistenceDirtyMarker.run();
        }
    }

    public int incrementAndGetTiredness() {
        this.tiredness++;
        persistenceDirtyMarker.run();
        return this.tiredness;
    }

    public void setExp(int newexp) {
        if (exp != newexp) {
            exp = newexp;
            persistenceDirtyMarker.run();
        }
    }

    public void setLevel(int newlevel) {
        if (level != newlevel) {
            level = newlevel;
            persistenceDirtyMarker.run();
        }
    }

    public void setItemId(int newitemid) {
        this.itemid = newitemid;
    }

    public void setSkillId(int newskillid) {
        this.skillid = newskillid;
    }

    public void setActive(boolean set) {
        this.active = set;
    }

    public boolean isActive() {
        return active;
    }

    public void empty() {
        if (owner != null) {
            owner.getClient().getWorldServer().unregisterMountHunger(owner);
        }
        this.owner = null;
        persistenceDirtyMarker = () -> {};
    }

    record PersistenceSnapshot(int exp, int level, int tiredness) {}
}
