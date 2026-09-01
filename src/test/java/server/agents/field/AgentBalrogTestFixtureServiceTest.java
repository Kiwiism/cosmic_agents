package server.agents.field;

import constants.game.GameConstants;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBalrogTestFixtureServiceTest {
    @Test
    void poolCoversEverySupportedSecondJobWeaponClass() {
        List<AgentBalrogTestFixtureService.Build> builds = AgentBalrogTestFixtureService.ALL_BUILDS;

        assertEquals(AgentBalrogTestFixtureService.WeaponClass.values().length,
                builds.stream().map(AgentBalrogTestFixtureService.Build::weaponClass)
                        .distinct().count());
        assertTrue(builds.stream().allMatch(build -> {
            int jobId = build.job().getId();
            return jobId >= 110 && jobId <= 520 && switch (jobId % 100) {
                case 10, 20, 30 -> true;
                default -> false;
            };
        }));
    }

    @Test
    void eachBuildSpendsExactlyTheLevel60SecondJobBudgetInItsJobTree() throws Exception {
        for (AgentBalrogTestFixtureService.Build build : AgentBalrogTestFixtureService.ALL_BUILDS) {
            Map<Integer, Integer> finalTargets = new HashMap<>();
            build.spBuild().forEach(step -> finalTargets.put(step.skillId(), step.targetLevel()));
            assertEquals(91, finalTargets.values().stream().mapToInt(Integer::intValue).sum(),
                    build.buildId());
            assertTrue(finalTargets.keySet().stream().allMatch(
                    skillId -> GameConstants.isInJobTree(skillId, build.job().getId())), build.buildId());
            for (Map.Entry<Integer, Integer> target : finalTargets.entrySet()) {
                assertTrue(target.getValue() <= wzSkillMax(target.getKey()),
                        build.buildId() + ":" + target.getKey());
            }
        }
    }

    @Test
    void everyWeaponAndArmorPieceIsRealAndLevel60Legal() throws Exception {
        for (AgentBalrogTestFixtureService.Build build : AgentBalrogTestFixtureService.ALL_BUILDS) {
            WzEquip weapon = wzEquip(build.weaponItemId());
            assertTrue(weapon.requiredLevel() >= AgentBalrogTestFixtureService.MINIMUM_WEAPON_LEVEL
                    && weapon.requiredLevel() <= AgentBalrogTestFixtureService.LEVEL, build.buildId());
            assertEquals(expectedWeaponCategory(build.weaponClass()), build.weaponItemId() / 10_000,
                    build.buildId());
            assertEquals(expectedJobMask(build.job()), weapon.requiredJob(), build.buildId());
            assertTrue(weapon.requirement(build.apBuild().secondaryStat())
                    <= build.apBuild().secondaryTarget(), build.buildId());
            assertTrue(weapon.requirement(build.apBuild().primaryStat())
                    <= 303 - build.apBuild().secondaryTarget(), build.buildId());
            for (int gender = 0; gender <= 1; gender++) {
                HashSet<String> occupiedSlots = new HashSet<>();
                for (int itemId : build.equipment(gender)) {
                    WzEquip equip = wzEquip(itemId);
                    assertTrue(equip.requiredLevel() <= 60, build.buildId() + ":" + itemId);
                    assertTrue(occupiedSlots.add(equip.slot()),
                            build.buildId() + " duplicate slot " + equip.slot());
                }
            }
        }
    }

    @Test
    void seededSelectionIsStableAndCoversEverySecondJobPath() {
        List<AgentBalrogTestFixtureService.Build> first =
                AgentBalrogTestFixtureService.selectRoster(99L);
        List<AgentBalrogTestFixtureService.Build> second =
                AgentBalrogTestFixtureService.selectRoster(99L);

        assertEquals(first, second);
        assertEquals(12, first.size());
        assertEquals(12, first.stream().map(
                AgentBalrogTestFixtureService.Build::job).distinct().count());
    }

    @Test
    void rosterUsesDistinctHeadAndBodyClothingWithinEachClassFamily() throws Exception {
        List<AgentBalrogTestFixtureService.Build> roster =
                AgentBalrogTestFixtureService.selectRoster(99L);

        for (int gender = 0; gender <= 1; gender++) {
            Map<Integer, HashSet<Integer>> wornByFamily = new HashMap<>();
            for (int ordinal = 0; ordinal < roster.size(); ordinal++) {
                AgentBalrogTestFixtureService.Build build = roster.get(ordinal);
                int rank = AgentBalrogTestFixtureService.clothingRank(roster, ordinal);
                HashSet<Integer> worn = wornByFamily.computeIfAbsent(
                        build.job().getId() / 100, ignored -> new HashSet<>());
                List<Integer> clothing = build.equipment(gender, rank).stream()
                        .filter(itemId -> {
                            int category = itemId / 10_000;
                            return category >= 100 && category <= 106;
                        }).toList();
                assertTrue(clothing.stream().allMatch(worn::add),
                        build.job() + " repeats family clothing at rank " + rank);
                for (int itemId : clothing) {
                    WzEquip equip = wzEquip(itemId);
                    assertTrue(equip.requiredLevel() <= 60, build.job() + ":" + itemId);
                    assertEquals(expectedJobMask(build.job()), equip.requiredJob(),
                            build.job() + ":" + itemId);
                }
            }
        }
    }

    private static WzEquip wzEquip(int itemId) throws Exception {
        String folder = switch (itemId / 10_000) {
            case 100 -> "Cap";
            case 104 -> "Coat";
            case 105 -> "Longcoat";
            case 106 -> "Pants";
            case 107 -> "Shoes";
            case 108 -> "Glove";
            case 109 -> "Shield";
            default -> "Weapon";
        };
        Path path = Path.of("wz", "Character.wz", folder,
                String.format("%08d.img.xml", itemId));
        assertTrue(Files.isRegularFile(path), itemId + " must exist in Character.wz");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        NodeList nodes = document.getElementsByTagName("int");
        Map<String, Integer> values = new HashMap<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Element node = (Element) nodes.item(index);
            values.putIfAbsent(node.getAttribute("name"), Integer.parseInt(node.getAttribute("value")));
        }
        NodeList strings = document.getElementsByTagName("string");
        String slot = "";
        for (int index = 0; index < strings.getLength(); index++) {
            Element node = (Element) strings.item(index);
            if ("islot".equals(node.getAttribute("name"))) {
                slot = node.getAttribute("value");
                break;
            }
        }
        assertTrue(!slot.isBlank(), itemId + " must declare an equipment slot");
        return new WzEquip(values.getOrDefault("reqLevel", 0), values.getOrDefault("reqJob", 0),
                values.getOrDefault("reqSTR", 0), values.getOrDefault("reqDEX", 0),
                values.getOrDefault("reqINT", 0), values.getOrDefault("reqLUK", 0), slot);
    }

    private static int wzSkillMax(int skillId) throws Exception {
        int jobId = skillId / 10_000;
        Path path = Path.of("wz", "Skill.wz", jobId + ".img.xml");
        assertTrue(Files.isRegularFile(path), skillId + " must exist in Skill.wz");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        NodeList directories = document.getElementsByTagName("imgdir");
        for (int index = 0; index < directories.getLength(); index++) {
            Element skill = (Element) directories.item(index);
            if (!Integer.toString(skillId).equals(skill.getAttribute("name"))) continue;
            NodeList children = skill.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                if (children.item(childIndex) instanceof Element child
                        && "imgdir".equals(child.getTagName())
                        && "level".equals(child.getAttribute("name"))) {
                    int levels = 0;
                    NodeList levelChildren = child.getChildNodes();
                    for (int levelIndex = 0; levelIndex < levelChildren.getLength(); levelIndex++) {
                        if (levelChildren.item(levelIndex) instanceof Element level
                                && "imgdir".equals(level.getTagName())) levels++;
                    }
                    return levels;
                }
            }
        }
        throw new AssertionError("missing WZ skill " + skillId);
    }

    private static int expectedWeaponCategory(AgentBalrogTestFixtureService.WeaponClass weaponClass) {
        return switch (weaponClass) {
            case ONE_HANDED_SWORD -> 130;
            case ONE_HANDED_AXE -> 131;
            case ONE_HANDED_BLUNT -> 132;
            case DAGGER -> 133;
            case WAND -> 137;
            case STAFF -> 138;
            case TWO_HANDED_SWORD -> 140;
            case TWO_HANDED_AXE -> 141;
            case TWO_HANDED_BLUNT -> 142;
            case SPEAR -> 143;
            case POLEARM -> 144;
            case BOW -> 145;
            case CROSSBOW -> 146;
            case CLAW -> 147;
            case KNUCKLE -> 148;
            case GUN -> 149;
        };
    }

    private static int expectedJobMask(client.Job job) {
        return switch (job.getId() / 100) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 8;
            case 5 -> 16;
            default -> throw new IllegalArgumentException("unsupported fixture job " + job);
        };
    }

    private record WzEquip(int requiredLevel, int requiredJob, int strength, int dexterity,
                           int intelligence, int luck, String slot) {
        private int requirement(server.agents.capabilities.build.AgentBuildService.StatType stat) {
            return switch (stat) {
                case STR -> strength;
                case DEX -> dexterity;
                case INT -> intelligence;
                case LUK -> luck;
            };
        }
    }
}
