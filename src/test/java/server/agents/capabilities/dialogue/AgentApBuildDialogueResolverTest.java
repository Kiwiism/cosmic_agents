package server.agents.capabilities.dialogue;

import client.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentApBuildDialogueResolverTest {
    @Test
    void shouldResolveWarriorPureStrBuildLikeLegacyChat() {
        AgentApBuildDialogueResolver.ApBuildChoice choice =
                AgentApBuildDialogueResolver.resolve(Job.WARRIOR, 20, 4, 35, "pure str");

        assertEquals(AgentApBuildDialogueResolver.StatType.STR, choice.primaryStat());
        assertEquals(AgentApBuildDialogueResolver.StatType.DEX, choice.secondaryStat());
        assertEquals(4, choice.secondaryTarget());
        assertEquals("dexless it is! keeping dex at 20, rest into str", choice.confirmMessage());
        assertEquals("already doing dexless!", choice.alreadyMessage());
    }

    @Test
    void shouldResolveThiefDexlessBuildLikeLegacyChat() {
        AgentApBuildDialogueResolver.ApBuildChoice choice =
                AgentApBuildDialogueResolver.resolve(Job.THIEF, 25, 40, 4, "dexless");

        assertEquals(AgentApBuildDialogueResolver.StatType.LUK, choice.primaryStat());
        assertEquals(AgentApBuildDialogueResolver.StatType.DEX, choice.secondaryStat());
        assertEquals(4, choice.secondaryTarget());
        assertEquals("dexless it is! keeping dex at 25, rest into luk", choice.confirmMessage());
        assertEquals("already doing dexless!", choice.alreadyMessage());
    }

    @Test
    void shouldResolveMageFixedLukBuildLikeLegacyChat() {
        AgentApBuildDialogueResolver.ApBuildChoice choice =
                AgentApBuildDialogueResolver.resolve(Job.MAGICIAN, 4, 30, 4, "23 luk");

        assertEquals(AgentApBuildDialogueResolver.StatType.INT, choice.primaryStat());
        assertEquals(AgentApBuildDialogueResolver.StatType.LUK, choice.secondaryStat());
        assertEquals(23, choice.secondaryTarget());
        assertEquals("ok! keeping luk at 30, rest into int", choice.confirmMessage());
        assertEquals("already doing 23 luk build!", choice.alreadyMessage());
    }

    @Test
    void shouldResolveBowmanStrlessAndFixedStrBuildsLikeLegacyChat() {
        AgentApBuildDialogueResolver.ApBuildChoice pure =
                AgentApBuildDialogueResolver.resolve(Job.BOWMAN, 25, 4, 4, "strless");
        AgentApBuildDialogueResolver.ApBuildChoice fixed =
                AgentApBuildDialogueResolver.resolve(Job.BOWMAN, 25, 4, 12, "10 str");

        assertEquals(AgentApBuildDialogueResolver.StatType.DEX, pure.primaryStat());
        assertEquals(AgentApBuildDialogueResolver.StatType.STR, pure.secondaryStat());
        assertEquals(4, pure.secondaryTarget());
        assertEquals("strless it is! keeping str at 4, rest into dex", pure.confirmMessage());
        assertEquals(AgentApBuildDialogueResolver.StatType.DEX, fixed.primaryStat());
        assertEquals(AgentApBuildDialogueResolver.StatType.STR, fixed.secondaryStat());
        assertEquals(10, fixed.secondaryTarget());
        assertEquals("ok! keeping str at 12, rest into dex", fixed.confirmMessage());
    }

    @Test
    void shouldReturnNullWhenMessageDoesNotSelectSupportedBuild() {
        assertNull(AgentApBuildDialogueResolver.resolve(Job.PIRATE, 4, 4, 4, "dexless"));
        assertNull(AgentApBuildDialogueResolver.resolve(Job.WARRIOR, 4, 4, 4, "hello"));
    }
}
