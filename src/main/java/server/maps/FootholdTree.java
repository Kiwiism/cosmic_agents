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
package server.maps;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Matze
 */
public class FootholdTree {
    private FootholdTree nw = null;
    private FootholdTree ne = null;
    private FootholdTree sw = null;
    private FootholdTree se = null;
    private final List<Foothold> footholds = new ArrayList<>();
    private final Point p1;
    private final Point p2;
    private final Point center;
    private int depth = 0;
    private static final int maxDepth = 8;
    private int maxDropX;
    private int minDropX;
    private int minimumZMass = -1;

    public FootholdTree(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
        center = new Point((p2.x - p1.x) / 2, (p2.y - p1.y) / 2);
    }

    public FootholdTree(Point p1, Point p2, int depth) {
        this.p1 = p1;
        this.p2 = p2;
        this.depth = depth;
        center = new Point((p2.x - p1.x) / 2, (p2.y - p1.y) / 2);
    }

    public void insert(Foothold f) {
        if (f.getZMass() >= 0 && (minimumZMass < 0 || f.getZMass() < minimumZMass)) {
            minimumZMass = f.getZMass();
        }
        if (depth == 0) {
            if (f.getX1() > maxDropX) {
                maxDropX = f.getX1();
            }
            if (f.getX1() < minDropX) {
                minDropX = f.getX1();
            }
            if (f.getX2() > maxDropX) {
                maxDropX = f.getX2();
            }
            if (f.getX2() < minDropX) {
                minDropX = f.getX2();
            }
        }
        if (depth == maxDepth ||
                (f.getX1() >= p1.x && f.getX2() <= p2.x &&
                        f.getY1() >= p1.y && f.getY2() <= p2.y)) {
            footholds.add(f);
        } else {
            if (nw == null) {
                nw = new FootholdTree(p1, center, depth + 1);
                ne = new FootholdTree(new Point(center.x, p1.y), new Point(p2.x, center.y), depth + 1);
                sw = new FootholdTree(new Point(p1.x, center.y), new Point(center.x, p2.y), depth + 1);
                se = new FootholdTree(center, p2, depth + 1);
            }
            if (f.getX2() <= center.x && f.getY2() <= center.y) {
                nw.insert(f);
            } else if (f.getX1() > center.x && f.getY2() <= center.y) {
                ne.insert(f);
            } else if (f.getX2() <= center.x && f.getY1() > center.y) {
                sw.insert(f);
            } else {
                se.insert(f);
            }
        }
    }

    private void collectXMatches(Point p, List<Foothold> list) {
        for (Foothold foothold : footholds) {
            if (foothold.getX1() <= p.x && foothold.getX2() >= p.x) {
                list.add(foothold);
            }
        }
        if (nw != null) {
            if (p.x <= center.x && p.y <= center.y) {
                nw.collectXMatches(p, list);
            } else if (p.x > center.x && p.y <= center.y) {
                ne.collectXMatches(p, list);
            } else if (p.x <= center.x && p.y > center.y) {
                sw.collectXMatches(p, list);
            } else {
                se.collectXMatches(p, list);
            }
        }
    }

    private Foothold findWallR(Point p1, Point p2) {
        Foothold ret;
        for (Foothold f : footholds) {
            if (f.isWall() && f.getX1() >= p1.x && f.getX1() <= p2.x &&
                    f.getY1() >= p1.y && f.getY2() <= p1.y) {
                return f;
            }
        }
        if (nw != null) {
            if (p1.x <= center.x && p1.y <= center.y) {
                ret = nw.findWallR(p1, p2);
                if (ret != null) {
                    return ret;
                }
            }
            if ((p1.x > center.x || p2.x > center.x) && p1.y <= center.y) {
                ret = ne.findWallR(p1, p2);
                if (ret != null) {
                    return ret;
                }
            }
            if (p1.x <= center.x && p1.y > center.y) {
                ret = sw.findWallR(p1, p2);
                if (ret != null) {
                    return ret;
                }
            }
            if ((p1.x > center.x || p2.x > center.x) && p1.y > center.y) {
                ret = se.findWallR(p1, p2);
                return ret;
            }
        }
        return null;
    }

    public Foothold findWall(Point p1, Point p2) {
        if (p1.y != p2.y) {
            throw new IllegalArgumentException();
        }
        return findWallR(p1, p2);
    }

    public Foothold findBelow(Point p) {
        List<Foothold> xMatches = new ArrayList<>();
        collectXMatches(p, xMatches);
        // Foothold.compareTo compares overlapping vertical spans as equal, which is
        // not transitive for a mixed set of slopes and horizontals. Large tower maps
        // can therefore make TimSort reject the comparison contract. At a fixed x,
        // findBelow only needs the actual crossing height, so sort by that total key.
        xMatches.sort(Comparator
                .comparingDouble((Foothold foothold) -> footingY(foothold, p.x))
                .thenComparingInt(Foothold::getId));
        for (Foothold fh : xMatches) {
            if (!fh.isWall()) {
                if (fh.getY1() != fh.getY2()) {
                    int calcY;
                    double s1 = Math.abs(fh.getY2() - fh.getY1());
                    double s2 = Math.abs(fh.getX2() - fh.getX1());
                    double s4 = Math.abs(p.x - fh.getX1());
                    double alpha = Math.atan(s2 / s1);
                    double beta = Math.atan(s1 / s2);
                    double s5 = Math.cos(alpha) * (s4 / Math.cos(beta));
                    if (fh.getY2() < fh.getY1()) {
                        calcY = fh.getY1() - (int) s5;
                    } else {
                        calcY = fh.getY1() + (int) s5;
                    }
                    if (calcY >= p.y) {
                        return fh;
                    }
                } else {
                    if (fh.getY1() >= p.y) {
                        return fh;
                    }
                }
            }
        }
        return null;
    }

    private static double footingY(Foothold foothold, int x) {
        if (foothold.isWall()) return Double.POSITIVE_INFINITY;
        int dx = foothold.getX2() - foothold.getX1();
        if (dx == 0) return Double.POSITIVE_INFINITY;
        return foothold.getY1()
                + (double) (foothold.getY2() - foothold.getY1())
                * (x - foothold.getX1()) / dx;
    }

    public int getX1() {
        return p1.x;
    }

    public int getX2() {
        return p2.x;
    }

    public int getY1() {
        return p1.y;
    }

    public int getY2() {
        return p2.y;
    }

    public List<Foothold> getAllFootholds() {
        List<Foothold> result = new ArrayList<>();
        collectAll(result);
        return result;
    }

    private void collectAll(List<Foothold> result) {
        result.addAll(footholds);
        if (nw != null) {
            nw.collectAll(result);
            ne.collectAll(result);
            sw.collectAll(result);
            se.collectAll(result);
        }
    }

    public int getMaxDropX() {
        return maxDropX;
    }

    public int getMinDropX() {
        return minDropX;
    }

    public int getMinimumZMass() {
        return minimumZMass;
    }
}
