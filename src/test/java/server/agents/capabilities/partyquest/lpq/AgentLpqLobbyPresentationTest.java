package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLpqLobbyPresentationTest {
    @Test
    void fiveRecruitmentAgentsUseDistinctSlotsAroundTheEntryNpc() {
        assertEquals(-145, AgentLpqTestService.recruitmentSlotOffset(0));
        assertEquals(-95, AgentLpqTestService.recruitmentSlotOffset(1));
        assertEquals(-45, AgentLpqTestService.recruitmentSlotOffset(2));
        assertEquals(5, AgentLpqTestService.recruitmentSlotOffset(3));
        assertEquals(55, AgentLpqTestService.recruitmentSlotOffset(4));
    }
}
