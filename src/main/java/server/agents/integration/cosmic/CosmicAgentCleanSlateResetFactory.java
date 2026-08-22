package server.agents.integration.cosmic;

import server.agents.administration.AgentCleanSlateResetService;
import server.agents.administration.AgentCleanSlateRuntimeStateReset;
import server.agents.runtime.AgentCharacterMaintenanceRuntime;
import server.agents.runtime.AgentRuntimeRegistry;

public final class CosmicAgentCleanSlateResetFactory {
    private CosmicAgentCleanSlateResetFactory() {
    }

    public static AgentCleanSlateResetService create() {
        return new AgentCleanSlateResetService(
                CosmicAgentCleanSlateResetPort.INSTANCE,
                new AgentCleanSlateResetService.Hooks() {
                    @Override public boolean online(int characterId) {
                        return CosmicCharacterGateway.INSTANCE.findOnlineCharacterById(characterId) != null;
                    }

                    @Override public boolean runtimeActive(int characterId) {
                        return AgentRuntimeRegistry.hasActiveAgentCharacterId(characterId);
                    }

                    @Override public AgentCleanSlateResetService.MaintenanceLease acquire(int characterId) {
                        return AgentCharacterMaintenanceRuntime.acquire(characterId);
                    }

                    @Override public void clearAgentOsProgress(int characterId) throws Exception {
                        AgentCleanSlateRuntimeStateReset.clear(characterId);
                    }
                });
    }
}
