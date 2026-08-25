package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomContentTest {
    @Test
    void kingPepeInstanceRollsAndSpawnsOneOfAllThreeVariants() throws Exception {
        String script = Files.readString(Path.of("scripts/event/KingPepeAndYetis.js"));

        assertTrue(script.contains("[3300005, 3300006, 3300007]"));
        assertTrue(script.contains("Math.floor(Math.random() * bossMobIds.length)"));
        assertTrue(script.contains("LifeFactory.getMonster(bossMobId)"));
        assertTrue(script.contains("spawnMonsterOnGroundBelow"));
    }

    @Test
    void intoxicatedPigFieldAuthorsTheExplicitReturnPortalUsedByTheRuntime() throws Exception {
        String map = Files.readString(
                Path.of("wz/Map.wz/Map/Map1/106020401.img.xml"));

        assertTrue(map.contains("<imgdir name=\"4\"><string name=\"pn\" value=\"left00\""));
        assertTrue(map.contains("<int name=\"tm\" value=\"106020400\""));
    }

    @Test
    void truthQuestGrantsItsRequirementsBeforeConsumingThemForTheFinalReward() throws Exception {
        String actions = questSlice(
                Files.readString(Path.of("wz/Quest.wz/Act.img.xml")), 2336);

        assertTrue(actions.contains("<int name=\"id\" value=\"4032387\""));
        assertTrue(actions.contains("<int name=\"id\" value=\"4032386\""));
        assertTrue(actions.contains("<int name=\"id\" value=\"1082254\""));
    }

    @Test
    void recoveryQuestsRemainAuthoredServerContent() throws Exception {
        String sporeRecovery = Files.readString(Path.of("scripts/quest/2338.js"));
        String sealRecovery = Files.readString(Path.of("scripts/quest/2342.js"));

        assertTrue(sporeRecovery.contains("qm.gainItem(2430014, 1)"));
        assertTrue(sealRecovery.contains("qm.gainItem(4001318, 1)"));
    }

    @Test
    void lowStrBanditFallbackIsAPlannedCatalogDaggerWithLegalRequirements() throws Exception {
        String equipment = Files.readString(
                Path.of("src/main/resources/agents/field/victoria-level0-25-equipment.json"));
        String dagger = Files.readString(
                Path.of("wz/Character.wz/Weapon/01332013.img.xml"));

        assertTrue(equipment.contains("1332013"));
        assertTrue(dagger.contains("<int name=\"reqLevel\" value=\"22\""));
        assertTrue(dagger.contains("<int name=\"reqDEX\" value=\"40\""));
        assertTrue(dagger.contains("<int name=\"reqLUK\" value=\"55\""));
    }

    @Test
    void crossbowmanFallbackIsTheStrongestPlannedCatalogCrossbow() throws Exception {
        String equipment = Files.readString(
                Path.of("src/main/resources/agents/field/victoria-level0-25-equipment.json"));
        String crossbow = Files.readString(
                Path.of("wz/Character.wz/Weapon/01462003.img.xml"));

        assertTrue(equipment.contains("1462003"));
        assertTrue(crossbow.contains("<int name=\"reqLevel\" value=\"22\""));
        assertTrue(crossbow.contains("<int name=\"reqSTR\" value=\"22\""));
        assertTrue(crossbow.contains("<int name=\"reqDEX\" value=\"70\""));
    }

    private static String questSlice(String xml, int questId) {
        String marker = "<imgdir name=\"" + questId + "\">";
        int start = xml.indexOf(marker);
        int end = xml.indexOf("<imgdir name=\"" + (questId + 1) + "\">", start);
        assertTrue(start >= 0, "missing q" + questId + " WZ actions");
        return xml.substring(start, end < 0 ? xml.length() : end);
    }
}
