package server.agents.plans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlanReattachmentRuntimeTest {
    @Test
    void resolvesEveryDurableMapleIslandPlanIdentity() {
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.AMHERST,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-amherst-subphase"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.SOUTHPERRY,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-southperry-mvp"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.FULL_MAPLE_ISLAND,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-full-mvp"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.UNSUPPORTED,
                AgentPlanReattachmentRuntime.resumeKind("career:level15:5"));
    }
}
