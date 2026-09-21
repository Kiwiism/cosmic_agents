package server.agents.capabilities.build;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCharacterShaperTest {
    @Test
    void advancementChainPassesThroughEveryJob() {
        assertEquals(List.of(), AgentCharacterShaper.advancementChain(Job.BEGINNER));
        assertEquals(List.of(Job.WARRIOR), AgentCharacterShaper.advancementChain(Job.WARRIOR));
        assertEquals(List.of(Job.WARRIOR, Job.FIGHTER), AgentCharacterShaper.advancementChain(Job.FIGHTER));
        assertEquals(List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER), AgentCharacterShaper.advancementChain(Job.CRUSADER));
        // A fourth-job target must not skip the third job, or shaping stalls at second job forever.
        assertEquals(List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER, Job.HERO), AgentCharacterShaper.advancementChain(Job.HERO));
        assertEquals(List.of(Job.MAGICIAN, Job.CLERIC, Job.PRIEST, Job.BISHOP), AgentCharacterShaper.advancementChain(Job.BISHOP));
        assertEquals(List.of(Job.PIRATE, Job.GUNSLINGER, Job.OUTLAW, Job.CORSAIR), AgentCharacterShaper.advancementChain(Job.CORSAIR));
    }
}
