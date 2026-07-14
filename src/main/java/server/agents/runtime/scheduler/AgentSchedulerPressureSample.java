package server.agents.runtime.scheduler;

public record AgentSchedulerPressureSample(
        long nowMs,
        long queueLagP95Ms,
        int registrations,
        int ingressDepth,
        int ingressCapacity,
        int actionableReadyDepth,
        AgentServerHealthSnapshot serverHealth) {
    public AgentSchedulerPressureSample {
        queueLagP95Ms = Math.max(0L, queueLagP95Ms);
        registrations = Math.max(0, registrations);
        ingressDepth = Math.max(0, ingressDepth);
        ingressCapacity = Math.max(1, ingressCapacity);
        actionableReadyDepth = Math.max(0, actionableReadyDepth);
        if (serverHealth == null) {
            throw new IllegalArgumentException("Agent server health snapshot is required");
        }
    }

    public int ingressPercent() {
        return (int) Math.min(100L, ingressDepth * 100L / ingressCapacity);
    }
}
