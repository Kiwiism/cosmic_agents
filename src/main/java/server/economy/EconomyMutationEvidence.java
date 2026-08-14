package server.economy;

import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/** Exact participant deltas captured at the same boundary as durable settlement. */
public record EconomyMutationEvidence(List<ParticipantDelta> participants,
                                      Map<String, Object> operationEvidence) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public EconomyMutationEvidence {
        participants = List.copyOf(participants);
        operationEvidence = operationEvidence == null ? Map.of() : Map.copyOf(operationEvidence);
    }

    public EconomyMutationEvidence(List<ParticipantDelta> participants) {
        this(participants, Map.of());
    }

    static EconomyMutationEvidence between(EconomyParticipantSnapshot primaryBefore,
                                           EconomyParticipantSnapshot primaryAfter,
                                           EconomyParticipantSnapshot secondaryBefore,
                                           EconomyParticipantSnapshot secondaryAfter,
                                           Map<String, Object> operationEvidence) {
        List<ParticipantDelta> result = new ArrayList<>();
        result.add(delta(primaryBefore, primaryAfter));
        if (secondaryBefore != null) result.add(delta(secondaryBefore, secondaryAfter));
        return new EconomyMutationEvidence(result, operationEvidence);
    }

    static EconomyMutationEvidence between(EconomyParticipantSnapshot primaryBefore,
                                           EconomyParticipantSnapshot primaryAfter,
                                           EconomyParticipantSnapshot secondaryBefore,
                                           EconomyParticipantSnapshot secondaryAfter) {
        return between(primaryBefore, primaryAfter, secondaryBefore, secondaryAfter, Map.of());
    }

    public String json() {
        try { return JSON.writeValueAsString(this); }
        catch (JsonProcessingException failure) { throw new EconomyTransactionException("Could not encode economy evidence", failure); }
    }

    private static ParticipantDelta delta(EconomyParticipantSnapshot before,
                                          EconomyParticipantSnapshot after) {
        Map<String, Holding> left = holdings(before);
        Map<String, Holding> right = holdings(after);
        Set<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        List<ItemDelta> changes = new ArrayList<>();
        for (String key : keys) {
            Holding oldValue = left.get(key);
            Holding newValue = right.get(key);
            int beforeQuantity = oldValue == null ? 0 : oldValue.quantity;
            int afterQuantity = newValue == null ? 0 : newValue.quantity;
            if (beforeQuantity != afterQuantity) {
                Holding fact = newValue == null ? oldValue : newValue;
                changes.add(new ItemDelta(fact.itemId, fact.inventoryType, fact.fingerprint,
                        beforeQuantity, afterQuantity, Math.subtractExact(afterQuantity, beforeQuantity),
                        fact.attributes));
            }
        }
        CharacterProgression oldProgression = progression(before);
        CharacterProgression newProgression = progression(after);
        return new ParticipantDelta(before.characterId(), before.mesos(), after.mesos(),
                Math.subtractExact(after.mesos(), before.mesos()), oldProgression.level,
                newProgression.level, oldProgression.experience, newProgression.experience,
                changes);
    }

    private static Map<String, Holding> holdings(EconomyParticipantSnapshot snapshot) {
        Map<String, Holding> result = new HashMap<>();
        snapshot.inventories().forEach((type, inventory) -> {
            for (Item item : inventory.list()) {
                EconomyItemEvidence.Description description = EconomyItemEvidence.describe(item);
                Map<String, Object> attributes = description.attributes();
                String fingerprint = description.fingerprint();
                String key = type.name() + ':' + item.getItemId() + ':' + fingerprint;
                result.merge(key, new Holding(item.getItemId(), type.name(), fingerprint,
                                item.getQuantity(), attributes),
                        (one, two) -> new Holding(one.itemId, one.inventoryType, one.fingerprint,
                                Math.addExact(one.quantity, two.quantity), one.attributes));
            }
        });
        return result;
    }

    private static CharacterProgression progression(EconomyParticipantSnapshot snapshot) {
        var value = snapshot.progression();
        return value == null ? new CharacterProgression(0, 0)
                : new CharacterProgression(value.level(), value.experience());
    }

    public record ParticipantDelta(int characterId, int mesoBefore, int mesoAfter,
                                   int mesoDelta, int levelBefore, int levelAfter,
                                   int experienceBefore, int experienceAfter,
                                   List<ItemDelta> itemDeltas) {
        public ParticipantDelta { itemDeltas = List.copyOf(itemDeltas); }
    }
    public record ItemDelta(int itemId, String inventoryType, String fingerprint,
                            int quantityBefore, int quantityAfter, int quantityDelta,
                            Map<String, Object> attributes) {
        public ItemDelta { attributes = Map.copyOf(attributes); }
    }
    private record Holding(int itemId, String inventoryType, String fingerprint, int quantity,
                           Map<String, Object> attributes) { }
    private record CharacterProgression(int level, int experience) { }
}
