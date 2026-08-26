package server.agents.field;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;

/** Legal level-25 observation loadout for dedicated HPQ test Agents. */
public final class AgentHpqTestFixtureService {
    static final int RICE_CAKE_HAT = 1_002_798;
    private static final short HAT_SLOT = -1;
    private static final int HPQ_START_LEVEL = config.AgentTuning.intValue(
            "server.agents.field.AgentHpqTestFixtureService.HPQ_START_LEVEL");

    private AgentHpqTestFixtureService() {
    }

    public static PreparationResult prepare(AgentRuntimeEntry entry, long seed, long nowMs)
            throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned HPQ Agent is required");
        applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForKpq(
                        entry, HPQ_START_LEVEL, seed, nowMs);
        ensureRiceCakeHat(agent);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("HPQ fixture left unspent AP/SP for " + prepared.name());
        }
        return new PreparationResult(prepared.level(), prepared.career(), prepared.completeBuild(),
                prepared.remainingAp(), prepared.remainingSps(), prepared.weaponItemId(),
                prepared.weaponAttack());
    }

    static AgentHpqAppearanceCatalog.Appearance applyAppearance(Character agent, long seed) {
        AgentHpqAppearanceCatalog.Appearance appearance =
                AgentHpqAppearanceCatalog.select(seed);
        agent.setGender(appearance.gender());
        agent.setSkinColor(appearance.skinColor());
        agent.setHair(appearance.hairId());
        agent.setFace(appearance.faceId());
        return appearance;
    }

    static void equipRiceCakeHat(Character agent, InventoryGateway inventory) {
        Item equipped = agent.getInventory(InventoryType.EQUIPPED).getItem(HAT_SLOT);
        if (equipped != null && equipped.getItemId() == RICE_CAKE_HAT) {
            removeCompetingHats(agent, inventory);
            return;
        }

        Equip template = inventory.getEquipById(RICE_CAKE_HAT);
        if (template == null || !inventory.canWearEquipment(agent, template, HAT_SLOT)) {
            throw new IllegalStateException("HPQ fixture cannot legally equip rice-cake hat "
                    + RICE_CAKE_HAT);
        }
        Item hat = agent.getInventory(InventoryType.EQUIP).list().stream()
                .filter(item -> item.getItemId() == RICE_CAKE_HAT && item.getPosition() > 0)
                .findFirst()
                .orElse(null);
        if (hat == null) {
            if (!inventory.addItem(agent, RICE_CAKE_HAT, (short) 1)) {
                throw new IllegalStateException("HPQ fixture could not receive rice-cake hat "
                        + RICE_CAKE_HAT);
            }
            hat = agent.getInventory(InventoryType.EQUIP).list().stream()
                    .filter(item -> item.getItemId() == RICE_CAKE_HAT && item.getPosition() > 0)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "HPQ fixture rice-cake hat is missing after grant"));
        }
        inventory.moveItem(agent, InventoryType.EQUIP, hat.getPosition(), HAT_SLOT, (short) 1);
        Item equippedHat = agent.getInventory(InventoryType.EQUIPPED).getItem(HAT_SLOT);
        if (equippedHat == null || equippedHat.getItemId() != RICE_CAKE_HAT) {
            throw new IllegalStateException("HPQ fixture did not equip rice-cake hat "
                    + RICE_CAKE_HAT);
        }
        removeCompetingHats(agent, inventory);
    }

    public static void ensureRiceCakeHat(Character agent) {
        equipRiceCakeHat(agent, AgentInventoryGatewayRuntime.inventory());
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
    }

    private static void removeCompetingHats(Character agent, InventoryGateway inventory) {
        var equip = agent.getInventory(InventoryType.EQUIP);
        if (equip == null) return;
        for (Item item : java.util.List.copyOf(equip.list())) {
            if (item != null && item.getItemId() != RICE_CAKE_HAT
                    && "Cp".equals(inventory.getEquipmentSlot(item.getItemId()))) {
                inventory.removeFromSlot(
                        agent, InventoryType.EQUIP, item.getPosition(), (short) 1, false);
            }
        }
    }

    public record PreparationResult(int level, String career, boolean completeBuild,
                                    int remainingAp, int[] remainingSps,
                                    int weaponItemId, int weaponAttack) {
        public PreparationResult {
            remainingSps = remainingSps == null ? new int[0] : remainingSps.clone();
        }

        @Override public int[] remainingSps() { return remainingSps.clone(); }
    }
}
