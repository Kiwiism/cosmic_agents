package server.agents.capabilities.presentation;

import server.agents.personality.AgentPersonalityProfile;

import java.util.ArrayList;
import java.util.List;

/** Pure deterministic mapping from semantic traits and an event to one cosmetic intent. */
public final class AgentPersonalityPresentationResolver {
    private static final long ACTIVATION_DOMAIN = 0x4143544956415445L;
    private static final long INTENT_DOMAIN = 0x494E54454E542D31L;
    private static final long DELAY_DOMAIN = 0x44454C41592D2D31L;
    private static final long DURATION_DOMAIN = 0x4455524154494F4EL;

    private AgentPersonalityPresentationResolver() {
    }

    public static AgentPresentationDecision resolve(AgentPersonalityProfile profile,
                                                    long behaviorSeed,
                                                    long sequence,
                                                    AgentPresentationTrigger trigger,
                                                    long occurredAtMs) {
        if (profile == null || trigger == null || sequence <= 0 || occurredAtMs < 0) {
            return null;
        }
        AgentPersonalityProfile.Traits traits = profile.traits();
        int chance = activationChance(traits, trigger);
        long activation = AgentPresentationDeterministicRandom.sample(
                behaviorSeed, sequence, ACTIVATION_DOMAIN ^ trigger.ordinal());
        if (AgentPresentationDeterministicRandom.bounded(activation, 100) >= chance) {
            return null;
        }

        AgentPresentationIntent intent = chooseIntent(traits, trigger,
                AgentPresentationDeterministicRandom.sample(
                        behaviorSeed, sequence, INTENT_DOMAIN ^ trigger.ordinal()));
        long delayMinimum = Math.max(150L, 900L - traits.activity() * 6L);
        long delayMaximum = delayMinimum + 500L + traits.patience() * 18L;
        long delayMs = AgentPresentationDeterministicRandom.range(
                AgentPresentationDeterministicRandom.sample(behaviorSeed, sequence, DELAY_DOMAIN),
                delayMinimum, delayMaximum);
        int durationMs = duration(intent, traits,
                AgentPresentationDeterministicRandom.sample(behaviorSeed, sequence, DURATION_DOMAIN));
        return new AgentPresentationDecision(intent, trigger, occurredAtMs + delayMs, durationMs);
    }

    private static int activationChance(AgentPersonalityProfile.Traits traits,
                                        AgentPresentationTrigger trigger) {
        int chance = 18 + traits.expressiveness() / 3 + traits.activity() / 5
                - traits.routinePreference() / 8;
        chance += switch (trigger) {
            case OBSERVER_PRESENT -> 15;
            case OBJECTIVE_COMPLETED -> 10;
            case ARRIVAL, COMBAT_IDLE -> 6;
            case SESSION_STARTED -> 2;
            case MOB_KILLED -> -4;
            case COMBAT_ENGAGED -> -8;
        };
        return Math.max(5, Math.min(80, chance));
    }

    private static AgentPresentationIntent chooseIntent(
            AgentPersonalityProfile.Traits traits,
            AgentPresentationTrigger trigger,
            long sample) {
        List<WeightedIntent> choices = new ArrayList<>();
        choices.add(new WeightedIntent(AgentPresentationIntent.WAIT, 20 + traits.patience()));
        choices.add(new WeightedIntent(AgentPresentationIntent.TURN, 10 + traits.expressiveness() / 2));
        choices.add(new WeightedIntent(AgentPresentationIntent.PRONE, 5 + traits.expressiveness() / 3));
        choices.add(new WeightedIntent(AgentPresentationIntent.PRONE_TAP,
                2 + traits.activity() / 4 + traits.expressiveness() / 5));
        choices.add(new WeightedIntent(AgentPresentationIntent.SHUFFLE,
                5 + traits.activity() / 2 + traits.curiosity() / 4));
        choices.add(new WeightedIntent(AgentPresentationIntent.HOP,
                Math.max(0, traits.activity() + traits.riskTolerance() - 85) / 3));
        choices.add(new WeightedIntent(AgentPresentationIntent.LINGER,
                5 + traits.patience() / 2));
        if (trigger == AgentPresentationTrigger.MOB_KILLED
                || trigger == AgentPresentationTrigger.COMBAT_IDLE) {
            choices.add(new WeightedIntent(AgentPresentationIntent.COMBAT_PAUSE,
                    15 + traits.patience() / 2));
            choices.add(new WeightedIntent(AgentPresentationIntent.COMBAT_REPOSITION,
                    10 + traits.activity() / 2 + traits.curiosity() / 3));
        }
        int total = choices.stream().mapToInt(WeightedIntent::weight).sum();
        int roll = AgentPresentationDeterministicRandom.bounded(sample, total);
        for (WeightedIntent choice : choices) {
            if (roll < choice.weight()) {
                return choice.intent();
            }
            roll -= choice.weight();
        }
        return AgentPresentationIntent.WAIT;
    }

    private static int duration(AgentPresentationIntent intent,
                                AgentPersonalityProfile.Traits traits,
                                long sample) {
        int minimum;
        int maximum;
        switch (intent) {
            case TURN -> {
                minimum = 300;
                maximum = 700;
            }
            case COMBAT_PAUSE -> {
                minimum = 700;
                maximum = 1_600;
            }
            case LINGER -> {
                minimum = 1_500;
                maximum = 2_500 + traits.patience() * 20;
            }
            default -> {
                minimum = 1_200;
                maximum = 2_200 + traits.expressiveness() * 12;
            }
        }
        return (int) AgentPresentationDeterministicRandom.range(sample, minimum, maximum);
    }

    private record WeightedIntent(AgentPresentationIntent intent, int weight) {
    }
}
