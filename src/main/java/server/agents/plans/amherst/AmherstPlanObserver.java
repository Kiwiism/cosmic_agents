package server.agents.plans.amherst;

@FunctionalInterface
public interface AmherstPlanObserver {
    AmherstPlanObserver NONE = message -> { };

    void publish(String message);
}
