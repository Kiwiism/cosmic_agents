package server.agents.capabilities.partyquest.kpq;

import client.Character;
import config.AgentYamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.partyquest.dialogue.AgentPartyQuestDialogueJudge;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevJudgmentMode;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Mixed-party KPQ guidance driven by authoritative session and NPC state. */
public final class AgentKpqHumanDialogueRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqHumanDialogueRuntime.class);
    private static final String ASKS_COUPON_COUNT = "asks_coupon_count";
    private static final long CHAT_RESPONSE_COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqHumanDialogueRuntime.CHAT_RESPONSE_COOLDOWN_MS");
    private static final long RESPONSE_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqHumanDialogueRuntime.RESPONSE_MINIMUM_MS");
    private static final long RESPONSE_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqHumanDialogueRuntime.RESPONSE_MAXIMUM_MS");
    private static final long DELAYED_PROMPT_AFTER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqHumanDialogueRuntime.DELAYED_PROMPT_AFTER_MS");
    private static final long DELAYED_PROMPT_COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqHumanDialogueRuntime.DELAYED_PROMPT_COOLDOWN_MS");

    private AgentKpqHumanDialogueRuntime() {
    }

    public static void observeChat(Character speaker, String message, long nowMs) {
        if (speaker == null || message == null || message.isBlank()) return;
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(speaker.getId());
        AgentKpqMemberState member = session == null ? null : session.member(speaker.getId());
        if (session == null || session.phase() != AgentKpqSession.Phase.STAGE_1
                || member == null || member.memberType() != AgentKpqMemberState.MemberType.HUMAN
                || speaker.getMapId() != AgentKpqDefinition.STAGE_1_MAP) {
            return;
        }
        if (AgentKpqHumanDialoguePolicy.asksForCouponCount(message)) {
            scheduleCouponAnswer(speaker, session, nowMs);
            return;
        }
        // Deterministic policy missed: optionally let TypeSafe judge the same question.
        JevJudgmentMode mode = JevJudgmentMode.parse(
                AgentYamlConfig.config.agent.AGENT_TYPESAFE_PARTY_QUEST_DIALOGUE_MODE);
        if (!mode.asks()) return;
        Map<String, Object> state = AgentPartyQuestDialogueJudge.state(
                speaker.getName(), message, "Kerning City Party Quest", "Stage 1",
                "each member asks Cloto for a question and reports how many coupons it needs",
                speaker.getId() == session.eventLeaderId() ? "party leader" : "party member");
        String sessionId = session.sessionId();
        AgentPartyQuestDialogueJudge.judge(JevClient.runtime(), state, List.of(
                new AgentPartyQuestDialogueJudge.Question(ASKS_COUPON_COUNT,
                        "Is the speaker asking how many coupons or tickets they need, or asking for the answer to their stage question?")))
                .thenAccept(verdict -> {
                    if (verdict.isEmpty()) return;
                    boolean yes = verdict.get().yes(ASKS_COUPON_COUNT);
                    log.info("[typesafe-kpq] mode={} speaker={} message=\"{}\" {} -> {}",
                            mode, speaker.getName(), message, verdict.get().summary(), yes ? "coupon question" : "not a coupon question");
                    if (!yes || !mode.acts()) return;
                    AgentKpqSession current = AgentKpqSessionRegistry.forMember(speaker.getId());
                    if (current == null || !current.sessionId().equals(sessionId)) return;
                    scheduleCouponAnswer(speaker, current, System.currentTimeMillis());
                });
    }

    private static void scheduleCouponAnswer(Character speaker, AgentKpqSession session, long nowMs) {
        if (!session.claimDialogue("coupon-help-" + speaker.getId(), nowMs, CHAT_RESPONSE_COOLDOWN_MS)) {
            return;
        }
        long delayMs = responseDelayMs(session.seed(), speaker.getId(),
                RESPONSE_MINIMUM_MS, RESPONSE_MAXIMUM_MS);
        AgentSchedulerRuntime.schedule(
                () -> answerCouponQuestion(speaker, session.sessionId()), delayMs);
    }

    private static void answerCouponQuestion(Character speaker, String sessionId) {
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(speaker.getId());
        AgentKpqMemberState member = session == null ? null : session.member(speaker.getId());
        if (session == null || !session.sessionId().equals(sessionId)
                || session.phase() != AgentKpqSession.Phase.STAGE_1
                || member == null || member.memberType() != AgentKpqMemberState.MemberType.HUMAN
                || speaker.getMapId() != AgentKpqDefinition.STAGE_1_MAP) {
            return;
        }
        Character responder = friendlySpeaker(session);
        if (responder == null) return;
        int target = AgentKpqDefinition.couponTarget(
                AgentPartyQuestGatewayRuntime.partyQuest().playerGrid(speaker));
        if (target > 0) {
            member.markQuestionRequested();
            member.setCouponTarget(target);
            AgentKpqDialogue.sayMapNow(responder,
                    AgentKpqHumanDialoguePolicy.couponAnswer(speaker.getName(), target));
        } else if (speaker.getId() == session.eventLeaderId()) {
            AgentKpqDialogue.sayMapNow(responder,
                    speaker.getName() + ", the party leader doesn't need a coupon question in Stage 1.");
        } else {
            AgentKpqDialogue.sayMapNow(responder,
                    speaker.getName() + ", talk to Cloto first so she can assign your coupon question.");
        }
    }

    static void remindStageOne(AgentKpqSession session, Character human) {
        if (session == null || human == null
                || !session.narrateOnce("human-stage1-help-" + human.getId())) return;
        Character speaker = friendlySpeaker(session);
        if (speaker != null) {
            AgentKpqDialogue.sayMapNow(speaker, human.getName()
                    + ", talk to Cloto first and collect the number she gives you. "
                    + "If you're unsure, ask us how many coupons you need. "
                    + "Just follow what the leader tells you.");
        }
    }

    static void promptDelayed(
            AgentKpqSession session, Character human, String task, long nowMs) {
        promptDelayed(session, human, task, nowMs, false);
    }

    static void promptDelayed(
            AgentKpqSession session, Character human, String task, long nowMs,
            boolean allowLeaderTeasing) {
        if (session == null || human == null || task == null
                || session.mode() != AgentKpqSession.Mode.TEST_OBSERVATION) {
            return;
        }
        String waitKey = "human-wait-" + session.phase() + '-' + human.getId()
                + '-' + Integer.toUnsignedString(task.hashCode());
        long waitedMs = session.dialogueWaitElapsedMs(waitKey, nowMs);
        if (waitedMs < DELAYED_PROMPT_AFTER_MS
                || !session.claimDialogue("human-delay-" + session.phase() + '-'
                        + human.getId(), nowMs, DELAYED_PROMPT_COOLDOWN_MS)) {
            return;
        }
        long turn = Math.max(0L, (waitedMs - DELAYED_PROMPT_AFTER_MS)
                / Math.max(1L, DELAYED_PROMPT_COOLDOWN_MS));
        Character speaker = socialSpeaker(session, turn);
        if (speaker != null) {
            boolean partyLeader = human.getId() == session.eventLeaderId();
            AgentKpqDialogue.sayMapNow(speaker, AgentKpqHumanDialoguePolicy.delayedPrompt(
                    human.getName(), task, session.seed(), turn,
                    partyLeader, !partyLeader || allowLeaderTeasing));
        }
    }

    static long responseDelayMs(long seed, int characterId, long minimumMs, long maximumMs) {
        long minimum = Math.max(0L, Math.min(minimumMs, maximumMs));
        long maximum = Math.max(minimum, Math.max(minimumMs, maximumMs));
        if (minimum == maximum) return minimum;
        return minimum + Math.floorMod(seed + characterId * 131L,
                maximum - minimum + 1L);
    }

    private static Character friendlySpeaker(AgentKpqSession session) {
        List<Character> agents = agents(session);
        return agents.isEmpty() ? null : agents.get(Math.floorMod(session.seed(), agents.size()));
    }

    private static Character socialSpeaker(AgentKpqSession session, long turn) {
        List<Character> agents = agents(session);
        if (agents.isEmpty()) return null;
        int friendly = Math.floorMod(session.seed(), agents.size());
        int offset = agents.size() == 1 ? 0 : 1 + Math.floorMod(turn, agents.size() - 1);
        return agents.get((friendly + offset) % agents.size());
    }

    private static List<Character> agents(AgentKpqSession session) {
        return session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .map(member -> agentCharacter(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static Character agentCharacter(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        return entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
    }
}
