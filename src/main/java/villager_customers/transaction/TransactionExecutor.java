package villager_customers.transaction;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.logistics.packager.InventorySummary;
import com.zurrtum.create.content.logistics.packager.PackagerBlockEntity;
import com.zurrtum.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.zurrtum.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.infrastructure.items.ContainerExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.model.MatchRule;
import villager_customers.model.NuggetConversion;
import villager_customers.model.StackShape;

/**
 * Runs the per-unit loop that executes a matched offer against a shop (`docs/spec/domains/transaction.md`
 * `TRANSACTION-REQ-002`..`TRANSACTION-REQ-007`; `decisions/DEC-006-direct-draw.md`).
 *
 * <p>The direct draw (`TRANSACTION-REQ-004`) needs no mixin into Create Fly: every
 * {@link net.minecraft.world.Container} is already soft-implemented as {@link ContainerExtension} by
 * Create Fly's own {@code com.zurrtum.create.mixin.ContainerMixin}, which is how the real player
 * checkout already draws stock and pays into a box ({@code Container.insert}, confirmed in the
 * research pass). This ticket (`VC-3`) found the symmetric public {@code Container.extract(ItemStack,
 * int)}, used the same way Create Fly's own {@code PackagerBlockEntity.attemptToSend} draws from a
 * packager's target inventory. The network's packagers are found by walking the stock ticker's own
 * {@code LogisticallyLinkedBehaviour} frequency (the same {@code getAllPresent} call
 * {@code LogisticsManager.createSummaryOfNetwork} uses to build a stock summary), filtered to
 * {@code PackagerLinkBlockEntity} and resolved to the {@code PackagerBlockEntity} each one names —
 * every step a public method or a public field, so no accessor mixin was needed for `ARCH-DEC-004`.
 */
public final class TransactionExecutor {
    /** {@code VC-14}: instrumentation for Kevin's live payment-box bug hunt, greppable as {@code VC14}. */
    private static final Logger LOGGER = LoggerFactory.getLogger("villager_customers");

    /**
     * The xp one {@code create:experience_nugget} is worth, read from
     * {@code ExperienceNuggetItem.use()} in the Create Fly jar at this ticket: {@code 3.0f} xp per
     * nugget, matching upstream Create and `decisions/DEC-007-xp-nuggets.md`'s proposed default.
     */
    private static final int NUGGET_XP = 3;

    /**
     * Test-only seam (`VC-13`): invoked once, right after {@code countSpace(payment)} has passed
     * and before {@code insert(payment)} runs, so a game test can force the box's real content to
     * change in between — exactly the race {@code countSpace}'s own pre-check cannot see coming,
     * since a filled-in-advance box is already caught by that pre-check itself (confirmed by
     * `javap -p -c` on `ContainerMixin`: {@code countSpace(List)} and {@code insert(List)} run the
     * same slot-major, entry-minor bin-packing algorithm, so they only disagree when the box's
     * content moves between the two calls). A no-op in production.
     */
    private static Runnable beforeInsertHookForTesting = () -> {
    };

    private TransactionExecutor() {
    }

    /**
     * Executes as many units of {@code offer} against {@code shop} as the offer's uses and the
     * shop's stock and payment box allow, stopping at the first refusal
     * (`TRANSACTION-REQ-007`).
     */
    public static Result execute(ServerLevel level, AbstractVillager villager, MerchantOffer offer, ShopAccess shop) {
        if (!matches(offer, shop)) {
            return new Result(0, Result.Reason.NO_MATCH);
        }

        // TRANSACTION-REQ-011: matches() has already confirmed the shop's one non-empty goods stack
        // satisfies offer.getItemCostA()'s predicate; every unit of this visit draws that same real,
        // component-bearing stack (not a stack synthesised from the offer alone), so the exact-match
        // extraction below only ever pulls stock that actually satisfies the offer.
        ItemStack goodsTemplate = singleGoodsStack(shop);

        int completed = 0;
        while (true) {
            if (offer.getUses() >= offer.getMaxUses()) {
                return new Result(completed, Result.Reason.OUT_OF_USES);
            }

            ItemStack goods = goodsTemplate.copy();
            ItemStack price = offer.getResult().copy();
            // The orb's own roll (TRANSACTION-REQ-010): vanilla's Villager.rewardTradeXp would spawn
            // an ExperienceOrb worth exactly 3 + random.nextInt(4) xp; the mixin suppresses that orb
            // for a mod-driven unit (VillagerRewardTradeXpMixin, gated on ModDrivenTrade below), and
            // this is the same formula, rolled from the level's own random rather than the villager's.
            int orbXp = 3 + level.getRandom().nextInt(4);
            int nuggetCount = NuggetConversion.nuggets(orbXp, NUGGET_XP);
            ItemStack nuggets = nuggetCount > 0 ? new ItemStack(AllItems.EXP_NUGGET, nuggetCount) : ItemStack.EMPTY;

            // getAccurateSummary() (a per-tick cache, docs/spec/domains/transaction.md TRANSACTION-REQ-002)
            // rather than getRecentSummary() (cached for 20 ticks): a mod-driven visit is rare enough
            // that the always-fresh read is worth it, and it keeps this check from seeing stock a
            // player only just placed, or drew, as stale.
            StockTickerBlockEntity ticker = shop.ticker();
            LOGGER.info(
                "VC14 unit entry villager={} shop={} ticker={} tickerId={} tickerRemoved={} box=[{}]", villager.getUUID(),
                shop.pos().toShortString(), ticker.getBlockPos().toShortString(), System.identityHashCode(ticker), ticker.isRemoved(),
                describeBox(ticker.getReceivedPaymentsHandler())
            );
            InventorySummary summary = ticker.getAccurateSummary();
            if (summary.getCountOf(goods) < goods.getCount()) {
                return new Result(completed, Result.Reason.STOCK_TOO_LOW);
            }

            List<ItemStack> payment = new ArrayList<>(2);
            payment.add(price);
            if (!nuggets.isEmpty()) {
                payment.add(nuggets);
            }
            ContainerExtension box = (ContainerExtension) ticker.getReceivedPaymentsHandler();
            if (!box.countSpace(payment)) {
                return new Result(completed, Result.Reason.BOX_FULL);
            }

            // VC-13: a unit is atomic — the payment lands before the goods ever leave the network,
            // and it lands in full or not at all. countSpace(payment) above is only a fast
            // pre-check; box.insert(payment)'s own returned leftovers are the ground truth, since
            // the box's real content can move between the two calls (another unit of this same
            // visit, or a different customer villager's own unit, landing first) even though the
            // two methods agree, bytecode-for-bytecode, on a container whose content does not move
            // in between (VC-13 Findings). Any leftover means the box did not, after all, take the
            // whole payment: pull back out exactly what did land — via ContainerExtension.extract,
            // matched the same way ContainerExtension.matches itself matches slot contents — before
            // the goods are ever touched, and refuse the unit as BOX_FULL.
            beforeInsertHookForTesting.run();
            List<ItemStack> leftover = box.insert(payment);
            LOGGER.info(
                "VC14 unit insert villager={} payment=[{}] leftover=[{}] box=[{}]", villager.getUUID(), describeStacks(payment),
                describeStacks(leftover), describeBox((Container) box)
            );
            if (!leftover.isEmpty()) {
                List<ItemStack> landed = landedStacks(box, payment, leftover);
                if (!landed.isEmpty()) {
                    box.extract(landed);
                }
                return new Result(completed, Result.Reason.BOX_FULL);
            }

            boolean drawnOk = drawFromNetwork(level, ticker, goods);
            // stockRemaining reuses getAccurateSummary()'s per-tick cache, already paid for above:
            // cheap enough to log every unit (VC-14).
            LOGGER.info(
                "VC14 unit draw villager={} drawnOk={} stockRemaining={}", villager.getUUID(), drawnOk,
                ticker.getAccurateSummary().getCountOf(goods)
            );
            if (!drawnOk) {
                // The full payment already landed for a unit whose goods could not, after all, be
                // drawn in full (a stale summary, or a concurrent draw within the same tick, exactly
                // as drawFromNetwork's own rollback already accounts for on the goods side): take
                // the payment back out before refusing, so a short draw never leaves an unearned
                // payment sitting in the box (VC-13). Every stack in `payment` landed in full (the
                // leftover check above already returned otherwise), so the whole list comes back out.
                box.extract(payment);
                return new Result(completed, Result.Reason.STOCK_TOO_LOW);
            }

            // box.insert(payment) above already ran Create Fly's own ContainerMixin default, which
            // calls setChanged() on any successful placement — StockTickerInventory.setChanged()
            // chains straight into StockTickerBlockEntity.notifyUpdate() (setChanged() + sendData(),
            // confirmed via javap -p -c on SyncedBlockEntity) — so the box is already marked dirty
            // and synced to nearby clients by this point. Called again here regardless (VC-13's own
            // Approach): cheap, and it keeps this unit's own persistence and sync from silently
            // depending on that Create Fly implementation detail continuing to hold.
            //
            // VC-17: right-click withdrawal proved receivedPayments is correct server-side, but the
            // goggle-less hover tooltip (`create.stock_ticker.contains_payments`, class
            // com.zurrtum.create.client.foundation.blockEntity.behaviour.tooltip
            // .StockTickerTooltipBehaviour, implements IHaveHoveringInformation — shown on plain
            // hover, no goggles required, per javap -p -c GoggleOverlayRenderer.renderOverlay:
            // IHaveGoggleInformation.addToGoggleTooltip is gated on GogglesItem.isWearingGoggles,
            // IHaveHoveringInformation.addToTooltip is not) did not show it. Read with javap -p -c,
            // StockTickerTooltipBehaviour.addToTooltip reads blockEntity.receivedPayments directly —
            // the client's own live StockTickerBlockEntity field, not a separate synced snapshot list
            // (newlyReceivedStockSnapshot/lastClientsideStockSnapshot are stock-summary fields, filled
            // only by receiveStockPacket, an unrelated packet) — gated on that container being
            // non-empty and on behaviour.mayAdministrate(viewingPlayer) (an unrelated per-network
            // ownership permission, not a trade-origin one; right-click withdrawal only needs the
            // weaker mayInteractMessage). javap -p -c on StockTickerBlockEntity.write(ValueOutput,
            // boolean clientPacket) shows receivedPayments.write(output) runs unconditionally, before
            // the `if (clientPacket)` branch that gates only ActiveLinks — so both the disk save and
            // every client packet already carry the payment box regardless of this flag, refuting the
            // ticket's write()-branch hypothesis. A player's own purchase
            // (StockTickerInteractionHandler.interactWithShop, javap -p -c) calls the very same
            // receivedPayments.insert(List) this executor calls, then StockTickerBlockEntity
            // .broadcastPackageRequest calls this.notifyUpdate() as its own last step — the identical
            // method StockTickerInventory.setChanged() already delegates to. VC-13's own Findings
            // (Part 2, "Sync ruled out too") independently reached the same conclusion for the box's
            // server-side content. Calling notifyUpdate() directly, instead of its two constituent
            // calls, makes this executor's own sync call textually and semantically the same call
            // Create Fly's own purchase path ends on, closing any remaining doubt that the two paths
            // could diverge — the client-bound data itself already matches before this change
            // (asserted by TransactionGameTest.aModDrivenUnitsPaymentIsClientSyncReady).
            ticker.notifyUpdate();

            // The flag around notifyTrade is what VillagerRewardTradeXpMixin keys off of to suppress
            // vanilla's own orb spawn inside Villager.rewardTradeXp for this unit only; cleared in
            // `finally` so a later player trade on this same villager still drops its orb
            // (TRANSACTION-REQ-010).
            ModDrivenTrade.begin();
            try {
                villager.notifyTrade(offer); // grants trade xp and calls offer.increaseUses() itself
            } finally {
                ModDrivenTrade.end();
            }
            LOGGER.info(
                "VC14 unit trade villager={} box=[{}] villagerXp={}", villager.getUUID(), describeBox((Container) box), villager.getVillagerXp()
            );
            completed++;
        }
    }

    /**
     * Whether {@code offer} and {@code shop} mirror each other (`TRANSACTION-REQ-001`, and, on top
     * of the pure shape match, `TRANSACTION-REQ-011`), exposed {@code public} for {@code VC-4}'s
     * restock search, which needs the same match rule this executor uses to test an offer against a
     * candidate shop before remembering it as a trip target (`docs/spec/domains/customer.md`
     * `CUSTOMER-REQ-003`).
     */
    public static boolean matches(MerchantOffer offer, ShopAccess shop) {
        StackShape offerCost = shapeOf(offer.getCostA());
        Optional<StackShape> offerSecondCost = offer.getItemCostB().map(cost -> shapeOf(cost.itemStack()));
        StackShape offerResult = shapeOf(offer.getResult());
        List<ItemStack> goodsStacks = shop.goods().stream().filter(stack -> !stack.isEmpty()).toList();
        List<StackShape> goods = goodsStacks.stream().map(TransactionExecutor::shapeOf).toList();
        StackShape price = shapeOf(shop.price());
        if (!MatchRule.matches(offerCost, offerSecondCost, offerResult, goods, price)) {
            return false;
        }

        // TRANSACTION-REQ-011: MatchRule (kept pure, no Minecraft imports) only ever compares plain
        // (itemId, count) shapes, which say nothing about components. Components live outside that
        // shape entirely, so the offer's own cost predicate is checked here instead, against the
        // shop's one real, component-bearing goods stack MatchRule.matches just confirmed exists
        // (goods.size() == 1 is part of what "matches" already means).
        return offer.getItemCostA().test(goodsStacks.get(0));
    }

    /**
     * The shop's single non-empty configured goods stack, real components included — the same stack
     * {@link #matches} already confirmed both mirrors the offer's cost shape and satisfies its cost
     * predicate (`TRANSACTION-REQ-001`, `TRANSACTION-REQ-011`). Callers only ever reach this after
     * {@link #matches} has returned {@code true}, so exactly one such stack is guaranteed to exist.
     */
    private static ItemStack singleGoodsStack(ShopAccess shop) {
        for (ItemStack stack : shop.goods()) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        throw new IllegalStateException("matches() already confirmed exactly one non-empty goods stack");
    }

    /**
     * Draws {@code goods.getCount()} matching items from the network's packagers; returns whether
     * the full count was drawn. On a shortfall (the stock summary said enough was available, but
     * fewer packagers actually delivered — a stale summary, or a concurrent change), every stack
     * already extracted is put back into the exact container it came from before returning
     * {@code false}, so a short draw never destroys items (VC-3 review finding).
     */
    private static boolean drawFromNetwork(ServerLevel level, StockTickerBlockEntity ticker, ItemStack goods) {
        UUID freqId = ticker.behaviour.freqId;
        int remaining = goods.getCount();
        List<ContainerExtension> sources = new ArrayList<>();
        List<Integer> amountsDrawn = new ArrayList<>();
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freqId, false)) {
            if (remaining <= 0) {
                break;
            }
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity packagerLink)) {
                continue;
            }
            PackagerBlockEntity packager = packagerLink.getPackager();
            if (packager == null) {
                continue;
            }
            Container target = packager.targetInventory.getInventory();
            if (target == null) {
                continue;
            }
            ContainerExtension source = (ContainerExtension) target;
            int drawnHere = source.extract(goods, remaining);
            if (drawnHere <= 0) {
                continue;
            }
            sources.add(source);
            amountsDrawn.add(drawnHere);
            remaining -= drawnHere;
        }

        if (remaining > 0) {
            for (int i = 0; i < sources.size(); i++) {
                returnDrawnStack(level, ticker, sources.get(i), goods, amountsDrawn.get(i));
            }
            return false;
        }
        return true;
    }

    /**
     * Puts a stack drawn from {@code source} back where it came from. If {@code source} has no room
     * for it again — it should not normally happen, since the slot the draw took it from was just
     * freed — whatever does not fit is dropped as an item entity at the ticker's position instead, so
     * a rollback never simply destroys the item.
     */
    private static void returnDrawnStack(ServerLevel level, StockTickerBlockEntity ticker, ContainerExtension source, ItemStack template, int count) {
        ItemStack returned = template.copyWithCount(count);
        List<ItemStack> leftover = source.insert(List.of(returned));
        for (ItemStack stack : leftover) {
            if (!stack.isEmpty()) {
                var pos = ticker.getBlockPos();
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    private static StackShape shapeOf(ItemStack stack) {
        return new StackShape(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }

    /** {@code VC-14}: every non-empty stack currently in {@code box}, formatted {@code count x id}. */
    private static String describeBox(Container box) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return describeStacks(stacks);
    }

    /** {@code VC-14}: {@code stacks}, formatted {@code count x id} each, joined for a log line. */
    private static String describeStacks(List<ItemStack> stacks) {
        List<String> parts = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                parts.add(formatStack(stack));
            }
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** {@code VC-14}: {@code stack} as {@code count x id}, e.g. {@code 1 x minecraft:emerald}. */
    private static String formatStack(ItemStack stack) {
        return stack.getCount() + " x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * What of {@code attempted} actually landed in {@code box}, given the {@code leftover} list
     * {@code box.insert(attempted)} itself returned (`VC-13`): each attempted stack's count, minus
     * however much of it came back as leftover, matched the same way {@link ContainerExtension}
     * itself matches slot contents ({@code box.matches}). A stack that landed in full is excluded
     * from {@code leftover} entirely by {@code insert}'s own contract, so this only ever reduces an
     * attempted stack's count, never inflates it.
     */
    private static List<ItemStack> landedStacks(ContainerExtension box, List<ItemStack> attempted, List<ItemStack> leftover) {
        List<ItemStack> landed = new ArrayList<>(attempted.size());
        for (ItemStack original : attempted) {
            int leftoverCount = 0;
            for (ItemStack stack : leftover) {
                if (box.matches(original, stack)) {
                    leftoverCount += stack.getCount();
                }
            }
            int landedCount = original.getCount() - leftoverCount;
            if (landedCount > 0) {
                landed.add(original.copyWithCount(landedCount));
            }
        }
        return landed;
    }

    /** Test-only: forces {@link #beforeInsertHookForTesting} (`VC-13`). */
    public static void setBeforeInsertHookForTesting(Runnable hook) {
        beforeInsertHookForTesting = hook;
    }

    /** Test-only: restores the production no-op hook. */
    public static void resetBeforeInsertHookForTesting() {
        beforeInsertHookForTesting = () -> {
        };
    }

    /** How a call to {@link #execute} ended: units completed and why it stopped (`TRANSACTION-REQ-007`). */
    public record Result(int unitsCompleted, Reason reason) {
        public enum Reason {
            /** The offer's cost/result do not mirror the shop's goods/price, or its cost has a second item. */
            NO_MATCH,
            /** The offer already had no uses left before this visit's loop began. */
            OUT_OF_USES,
            /** The network stock could not cover the next unit's goods (`TRANSACTION-FAIL-002`). */
            STOCK_TOO_LOW,
            /** The payment box has no room for the next unit's price plus its xp nuggets (`TRANSACTION-FAIL-003`). */
            BOX_FULL
        }
    }
}
