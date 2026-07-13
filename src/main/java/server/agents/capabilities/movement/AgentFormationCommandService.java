package server.agents.capabilities.movement;

import client.Character;
import server.agents.commands.AgentCommandNumberParser;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentFormationCommandService {
    private static final int MAX_FORMATION_DISTANCE_PX = 10_000;
    private static final Pattern FORMATION_PATTERN = Pattern.compile(
            "\\b(?:formation|form)\\b(?:\\s+(stagger|split|random|stack|spread|tight|loose|left|right|snap)(?:\\s+(\\d+|tight|loose|on|off))?)?",
            Pattern.CASE_INSENSITIVE);
    private static final String HELP = "formations: stagger/split/random/spread/left/right <px>, stack, tight, loose | snap <px/on/off>";

    private AgentFormationCommandService() {
    }

    public record Hooks(EntriesByLeader entriesByLeader,
                        FormationStateByLeader formationStateByLeader,
                        FormationStateWriter formationStateWriter,
                        FormationOffsetApplier formationOffsetApplier,
                        EntryReply entryReply,
                        LeaderMessage leaderMessage,
                        AgentFormationService.FormationState defaultFormation,
                        int defaultFollowStaggerPx,
                        int defaultSnapRangePx) {
    }

    @FunctionalInterface
    public interface EntriesByLeader {
        List<? extends AgentRuntimeEntry> entries(int leaderCharId);
    }

    @FunctionalInterface
    public interface FormationStateByLeader {
        AgentFormationService.FormationState state(int leaderCharId, AgentFormationService.FormationState defaultFormation);
    }

    @FunctionalInterface
    public interface FormationStateWriter {
        void put(int leaderCharId, AgentFormationService.FormationState formation);
    }

    @FunctionalInterface
    public interface FormationOffsetApplier {
        void apply(List<? extends AgentRuntimeEntry> entries, AgentFormationService.FormationState formation);
    }

    @FunctionalInterface
    public interface EntryReply {
        void reply(AgentRuntimeEntry entry, String message);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static boolean handleFormationCommand(Character leader, String message, Hooks hooks) {
        Matcher matcher = FORMATION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return false;
        }

        int leaderId = leader.getId();
        String typeToken = matcher.group(1);
        List<? extends AgentRuntimeEntry> entries = hooks.entriesByLeader().entries(leaderId);
        if (typeToken == null) {
            replyFirstOrLeader(leader, entries, HELP, hooks);
            return true;
        }

        AgentFormationService.FormationState current =
                hooks.formationStateByLeader().state(leaderId, hooks.defaultFormation());
        if (typeToken.equalsIgnoreCase("snap")) {
            handleSnapCommand(leader, entries, matcher.group(2), current, hooks);
            return true;
        }

        String pxToken = matcher.group(2);
        Integer defaultPx = defaultPx(pxToken, hooks.defaultFollowStaggerPx());
        if (defaultPx == null) {
            replyFirstOrLeader(leader, entries, invalidDistanceMessage(), hooks);
            return true;
        }
        AgentFormationService.FormationType type = formationType(typeToken);
        int px = switch (typeToken.toLowerCase()) {
            case "tight" -> 30;
            case "loose" -> 120;
            case "stack" -> 0;
            default -> defaultPx;
        };

        AgentFormationService.FormationState formation =
                new AgentFormationService.FormationState(type, px, current.snapRange());
        hooks.formationStateWriter().put(leaderId, formation);
        if (entries != null) {
            hooks.formationOffsetApplier().apply(entries, formation);
            if (!entries.isEmpty()) {
                String label = typeToken.toLowerCase() + (px > 0 ? " " + px + "px" : "");
                hooks.entryReply().reply(entries.get(0), "formation: " + label);
            }
        }
        return true;
    }

    public static boolean matchesCommand(String message) {
        return message != null && FORMATION_PATTERN.matcher(message).find();
    }

    private static void handleSnapCommand(Character leader,
                                          List<? extends AgentRuntimeEntry> entries,
                                          String qualifier,
                                          AgentFormationService.FormationState current,
                                          Hooks hooks) {
        if (qualifier == null) {
            String status = current.snapRange() > 0 ? "on (" + current.snapRange() + "px)" : "off";
            replyFirstOrLeader(leader, entries, "snap: " + status, hooks);
            return;
        }

        int newSnapRange;
        if (qualifier.equalsIgnoreCase("off")) {
            newSnapRange = 0;
        } else if (qualifier.equalsIgnoreCase("on")) {
            newSnapRange = current.snapRange() > 0 ? current.snapRange() : hooks.defaultSnapRangePx();
        } else {
            Integer parsedRange = AgentCommandNumberParser.parseIntInRange(
                    qualifier, 0, MAX_FORMATION_DISTANCE_PX);
            if (parsedRange == null) {
                replyFirstOrLeader(leader, entries, invalidDistanceMessage(), hooks);
                return;
            }
            newSnapRange = parsedRange;
        }

        AgentFormationService.FormationState formation =
                new AgentFormationService.FormationState(current.type(), current.px(), newSnapRange);
        hooks.formationStateWriter().put(leader.getId(), formation);
        String status = newSnapRange > 0 ? "on (" + newSnapRange + "px)" : "off";
        if (entries != null && !entries.isEmpty()) {
            hooks.entryReply().reply(entries.get(0), "snap: " + status);
        }
    }

    private static Integer defaultPx(String pxToken, int configuredDefault) {
        if (pxToken == null) {
            return configuredDefault;
        }
        if (pxToken.equalsIgnoreCase("tight")) {
            return 30;
        }
        if (pxToken.equalsIgnoreCase("loose")) {
            return 120;
        }
        if (pxToken.equalsIgnoreCase("on") || pxToken.equalsIgnoreCase("off")) {
            return configuredDefault;
        }
        return AgentCommandNumberParser.parseIntInRange(pxToken, 0, MAX_FORMATION_DISTANCE_PX);
    }

    private static String invalidDistanceMessage() {
        return "formation distance must be between 0 and " + MAX_FORMATION_DISTANCE_PX + "px";
    }

    private static AgentFormationService.FormationType formationType(String typeToken) {
        return switch (typeToken.toLowerCase()) {
            case "stack" -> AgentFormationService.FormationType.STACK;
            case "spread" -> AgentFormationService.FormationType.SPREAD;
            case "left" -> AgentFormationService.FormationType.LEFT;
            case "right" -> AgentFormationService.FormationType.RIGHT;
            case "random" -> AgentFormationService.FormationType.RANDOM;
            default -> AgentFormationService.FormationType.STAGGER;
        };
    }

    private static void replyFirstOrLeader(Character leader,
                                           List<? extends AgentRuntimeEntry> entries,
                                           String message,
                                           Hooks hooks) {
        if (entries != null && !entries.isEmpty()) {
            hooks.entryReply().reply(entries.get(0), message);
        } else {
            hooks.leaderMessage().send(leader, message);
        }
    }
}
