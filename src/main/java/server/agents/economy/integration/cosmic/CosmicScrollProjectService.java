package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemInformationProvider;
import server.ScrollTransactionService;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.NamedRandomStreams;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;
import tools.Randomizer;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Applies an owned project scroll through the same Cosmic mutation used by human packets. */
public final class CosmicScrollProjectService implements AutonomousFreeMarketBehavior.ScrollBehavior {
    private final UUID runId;
    private final NamedRandomStreams random;
    private final ItemInformationProvider items;

    public CosmicScrollProjectService(UUID runId, NamedRandomStreams random) {
        this(runId, random, ItemInformationProvider.getInstance());
    }

    CosmicScrollProjectService(UUID runId, NamedRandomStreams random, ItemInformationProvider items) {
        this.runId = Objects.requireNonNull(runId);
        this.random = Objects.requireNonNull(random);
        this.items = Objects.requireNonNull(items);
    }

    @Override
    public Result applyNext(Character agent, CommerceParticipant profile,
                            List<AgentNeed> needs, Instant logicalAt) {
        Optional<AgentNeed> project = needs.stream()
                .filter(need -> need.reason() == EconomicReason.SCROLL_UPGRADE)
                .filter(need -> agent.getInventory(InventoryType.USE).countById(need.itemId()) > 0)
                .sorted(Comparator.comparingInt(AgentNeed::itemId)).findFirst();
        if (project.isEmpty()) return Result.none();
        int scrollId = project.orElseThrow().itemId();
        Item scroll = agent.getInventory(InventoryType.USE).findById(scrollId);
        Optional<Equip> target = agent.getInventory(InventoryType.EQUIPPED).list().stream()
                .filter(Equip.class::isInstance).map(Equip.class::cast)
                .filter(equip -> equip.getUpgradeSlots() > 0 && items.canApplyScroll(scrollId, equip.getItemId()))
                .sorted(Comparator.comparingInt(Equip::getItemId).thenComparingInt(Equip::getPosition))
                .findFirst();
        if (scroll == null || target.isEmpty()) return Result.none();
        Equip equipment = target.orElseThrow();
        if (!agent.getClient().tryacquireClient())
            return new Result(true, false, scrollId, equipment.getItemId(), "CLIENT_BUSY", Map.of());
        try {
            AtomicReference<ScrollTransactionService.Result> applied = new AtomicReference<>();
            String key = "scroll:" + runId + ':' + profile.agentId() + ':' + logicalAt + ':'
                    + scrollId + ':' + equipment.getPosition();
            String summary = "scroll=" + scrollId + " equipment=" + equipment.getItemId();
            NamedRandomStreams.Stream stream = random.stream("agent." + profile.agentId() + ".scroll");
            Randomizer.withLongSource(stream::nextLong, () -> EconomyTransactionCoordinator.execute(
                    key, agent, null, EconomyOperationKind.SCROLL_APPLY, summary, context -> {
                        ScrollTransactionService.Result result = ScrollTransactionService.apply(
                                agent.getClient(), scroll.getPosition(), equipment.getPosition(), (byte) 0);
                        if (!result.applied()) throw new IllegalStateException(
                                "scroll project became invalid: " + result.outcome());
                        context.recordEvidence("scrollApplication", Map.of(
                                "scrollItemId", result.scrollItemId(),
                                "equipmentItemId", result.equipmentItemId(),
                                "outcome", result.outcome(),
                                "whiteScroll", result.whiteScroll(),
                                "rngStream", "agent." + profile.agentId() + ".scroll"));
                        applied.set(result);
                    }));
            ScrollTransactionService.Result result = applied.get();
            return new Result(true, true, scrollId, equipment.getItemId(), result.outcome(),
                    Map.of("whiteScroll", result.whiteScroll(),
                            "rngStream", "agent." + profile.agentId() + ".scroll"));
        } finally {
            agent.getClient().releaseClient();
        }
    }
}
