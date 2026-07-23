package server.agents.capabilities.npc;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.catalog.CatalogRecord;
import server.agents.catalog.NpcCatalogQuery;

import java.awt.Point;
import java.util.List;
import java.util.Optional;

public final class AgentNpcInteractionValidator {
    private static final long DEFAULT_DIALOGUE_DELAY_MS = config.AgentTuning.longValue("server.agents.capabilities.npc.AgentNpcInteractionValidator.DEFAULT_DIALOGUE_DELAY_MS");

    private final NpcCatalogQuery catalogQuery;

    public AgentNpcInteractionValidator() {
        this(null);
    }

    public AgentNpcInteractionValidator(NpcCatalogQuery catalogQuery) {
        this.catalogQuery = catalogQuery;
    }

    public AgentNpcInteractionResult validate(AgentNpcInteractionRequest request) {
        if (request == null) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "NPC interaction request is required", null);
        }
        if (request.npcId() <= 0) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "NPC id is required", request);
        }
        if (request.mapId() <= 0) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "map id is required", request);
        }

        Point npcPosition = resolveNpcPosition(request);
        if (catalogQuery != null && catalogQuery.findById(request.npcId()).isEmpty()) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "NPC is not present in the catalog", request);
        }
        if (catalogQuery != null && catalogQuery.placementsForNpc(request.npcId()).stream()
                .noneMatch(placement -> placement.intValue("mapId").orElse(-1) == request.mapId())) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                    "NPC is not cataloged in the requested map", request);
        }
        if (requiresQuestAction(request) && !hasCatalogAction(request)) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "NPC does not expose the requested quest action in catalog data", request);
        }
        if (request.hasRangeCheck() && npcPosition != null && distanceSq(request.agentPosition(), npcPosition)
                > (long) request.maxRangePx() * request.maxRangePx()) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.NOT_READY,
                    "agent is outside NPC interaction range", request);
        }

        Point approachPoint = selectApproachPoint(request).orElse(null);
        if (request.requireApproachPoint() && approachPoint == null) {
            return AgentNpcInteractionResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "no cataloged NPC approach point is available", request);
        }

        return AgentNpcInteractionResult.pending("NPC interaction validated; live execution is not wired yet",
                request, npcPosition, approachPoint, estimatedDialogueDelayMs(request));
    }

    private boolean requiresQuestAction(AgentNpcInteractionRequest request) {
        return catalogQuery != null && request.hasQuestId()
                && (request.type() == AgentNpcInteractionType.QUEST_START
                || request.type() == AgentNpcInteractionType.QUEST_COMPLETE);
    }

    private boolean hasCatalogAction(AgentNpcInteractionRequest request) {
        if (catalogQuery == null || !request.hasQuestId()) {
            return true;
        }
        String expected = request.type() == AgentNpcInteractionType.QUEST_START ? "quest-start" : "quest-complete";
        return catalogQuery.actionsForQuest(request.questId()).stream()
                .anyMatch(action -> action.intValue("npcId").orElse(-1) == request.npcId()
                        && expected.equalsIgnoreCase(action.stringValue("actionType").orElse("")));
    }

    private Point resolveNpcPosition(AgentNpcInteractionRequest request) {
        if (request.npcPosition() != null) {
            return new Point(request.npcPosition());
        }
        if (catalogQuery == null) {
            return null;
        }
        return catalogQuery.placementsForNpc(request.npcId()).stream()
                .filter(placement -> placement.intValue("mapId").orElse(-1) == request.mapId())
                .map(this::pointFromRecord)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private Optional<Point> selectApproachPoint(AgentNpcInteractionRequest request) {
        if (catalogQuery == null) {
            return Optional.empty();
        }
        List<CatalogRecord> candidates = catalogQuery.approachCandidates(request.npcId(), request.mapId());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        CatalogRecord selected = catalogQuery.seededApproachCandidate(request.npcId(), request.mapId(),
                request.randomSeed()).orElse(candidates.getFirst());
        return pointFromRecord(selected);
    }

    private long estimatedDialogueDelayMs(AgentNpcInteractionRequest request) {
        if (catalogQuery == null || !request.hasQuestId()) {
            return DEFAULT_DIALOGUE_DELAY_MS;
        }
        return catalogQuery.dialogueTiming(request.questId(), request.dialoguePhase())
                .flatMap(record -> record.longValue("estimatedDelayMs")
                        .or(() -> record.longValue("delayMs"))
                        .or(() -> record.longValue("medianDelayMs")))
                .orElse(DEFAULT_DIALOGUE_DELAY_MS);
    }

    private Optional<Point> pointFromRecord(CatalogRecord record) {
        Optional<Integer> x = record.intValue("x").or(() -> record.intValue("targetX"))
                .or(() -> record.intValue("interactX"));
        Optional<Integer> y = record.intValue("y").or(() -> record.intValue("targetY"))
                .or(() -> record.intValue("interactY"));
        if (x.isPresent() && y.isPresent()) {
            return Optional.of(new Point(x.get(), y.get()));
        }
        return Optional.empty();
    }

    private static long distanceSq(Point source, Point target) {
        long dx = source.x - target.x;
        long dy = source.y - target.y;
        return dx * dx + dy * dy;
    }
}
