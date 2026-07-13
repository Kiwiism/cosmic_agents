package server.agents.integration;

import java.lang.reflect.Method;
import java.util.List;

/** Closed audit inventory used to guard multi-shard scheduler rollout. */
public final class AgentGatewayAffinityCatalog {
    private static final List<Class<?>> GATEWAY_TYPES = List.of(
            AgentClientGateway.class,
            AgentPersistenceGateway.class,
            AgentQuestSyncGateway.class,
            AgentQuestSyncHandle.class,
            CharacterGateway.class,
            CombatGateway.class,
            InventoryGateway.class,
            LifeGateway.class,
            MakerGateway.class,
            MapGateway.class,
            MovementGateway.class,
            NpcGateway.class,
            PacketGateway.class,
            PartyGateway.class,
            PrimitiveCapabilityGateway.class,
            QuestGateway.class,
            SchedulerGateway.class,
            ShopGateway.class,
            SkillGateway.class,
            TradeGateway.class,
            AgentCombatStanceGateway.class,
            AgentTradeInviteGateway.class);
    private static final boolean MULTI_SHARD_READY = calculateMultiShardReady();

    private AgentGatewayAffinityCatalog() {
    }

    public static List<Class<?>> gatewayTypes() {
        return GATEWAY_TYPES;
    }

    public static AgentGatewayThreadAffinity affinity(Method method) {
        if (method == null) {
            throw new IllegalArgumentException("Gateway method is required");
        }
        AgentGatewayAffinity operation = method.getAnnotation(AgentGatewayAffinity.class);
        if (operation != null) {
            return operation.value();
        }
        return affinity(method.getDeclaringClass());
    }

    public static AgentGatewayThreadAffinity affinity(Class<?> gatewayType) {
        if (gatewayType == null) {
            throw new IllegalArgumentException("Gateway type is required");
        }
        AgentGatewayAffinity annotation = gatewayType.getAnnotation(AgentGatewayAffinity.class);
        if (annotation == null) {
            throw new IllegalStateException("Missing Agent gateway affinity: " + gatewayType.getName());
        }
        return annotation.value();
    }

    public static boolean multiShardReady() {
        return MULTI_SHARD_READY;
    }

    private static boolean calculateMultiShardReady() {
        return GATEWAY_TYPES.stream()
                .flatMap(type -> {
                    Method[] methods = type.getDeclaredMethods();
                    return methods.length == 0
                            ? java.util.stream.Stream.of(affinity(type))
                            : java.util.Arrays.stream(methods).map(AgentGatewayAffinityCatalog::affinity);
                })
                .noneMatch(affinity -> affinity == AgentGatewayThreadAffinity.UNSAFE_PENDING_REFACTOR
                        || affinity == AgentGatewayThreadAffinity.SERVER_EXECUTOR_REQUIRED);
    }
}
