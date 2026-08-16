package server.agents.runtime.activity;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Immutable controller registry; precedence only orders already-admitted compatibility work. */
public final class AgentActivityControllerRegistry {
    private final List<AgentActivityController> controllers;

    public AgentActivityControllerRegistry(List<? extends AgentActivityController> controllers) {
        if (controllers == null) {
            throw new IllegalArgumentException("Activity controllers are required");
        }
        List<AgentActivityController> ordered = new ArrayList<>(controllers);
        Set<String> ids = new HashSet<>();
        Set<AgentActivityKind> primaryKinds = new HashSet<>();
        for (AgentActivityController controller : ordered) {
            if (controller == null || controller.id() == null || controller.id().isBlank()) {
                throw new IllegalArgumentException("Every activity controller requires an id");
            }
            if (!ids.add(controller.id())) {
                throw new IllegalArgumentException(
                        "Duplicate activity controller id: " + controller.id());
            }
            if (controller.role() == null) {
                throw new IllegalArgumentException(
                        "Every activity controller requires a role: " + controller.id());
            }
            if (controller.role() == AgentActivityRole.PRIMARY) {
                if (controller.activityKind() == null) {
                    throw new IllegalArgumentException(
                            "Primary controller requires an activity kind: " + controller.id());
                }
                if (!primaryKinds.add(controller.activityKind())) {
                    throw new IllegalArgumentException(
                            "Duplicate primary activity kind: " + controller.activityKind());
                }
            } else if (controller.activityKind() != null) {
                throw new IllegalArgumentException(
                        "Only primary controllers declare an activity kind: " + controller.id());
            }
        }
        ordered.sort(Comparator.comparingInt(AgentActivityController::precedence).reversed());
        this.controllers = List.copyOf(ordered);
    }

    public List<AgentActivityController> controllers() {
        return controllers;
    }

    public Optional<AgentActivityController> find(String id) {
        return controllers.stream().filter(controller -> controller.id().equals(id)).findFirst();
    }
}
