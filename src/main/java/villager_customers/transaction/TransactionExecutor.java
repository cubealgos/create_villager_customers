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
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
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
    /**
     * The xp one {@code create:experience_nugget} is worth, read from
     * {@code ExperienceNuggetItem.use()} in the Create Fly jar at this ticket: {@code 3.0f} xp per
     * nugget, matching upstream Create and `decisions/DEC-007-xp-nuggets.md`'s proposed default.
     */
    private static final int NUGGET_XP = 3;

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

        int completed = 0;
        while (true) {
            if (offer.getUses() >= offer.getMaxUses()) {
                return new Result(completed, Result.Reason.OUT_OF_USES);
            }

            ItemStack goods = offer.getCostA().copy();
            ItemStack price = offer.getResult().copy();
            int nuggetCount = NuggetConversion.nuggets(offer.getXp(), NUGGET_XP);
            ItemStack nuggets = nuggetCount > 0 ? new ItemStack(AllItems.EXP_NUGGET, nuggetCount) : ItemStack.EMPTY;

            // getAccurateSummary() (a per-tick cache, docs/spec/domains/transaction.md TRANSACTION-REQ-002)
            // rather than getRecentSummary() (cached for 20 ticks): a mod-driven visit is rare enough
            // that the always-fresh read is worth it, and it keeps this check from seeing stock a
            // player only just placed, or drew, as stale.
            StockTickerBlockEntity ticker = shop.ticker();
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

            int drawn = drawFromNetwork(ticker, goods);
            if (drawn < goods.getCount()) {
                // The summary said the network had enough; a concurrent draw within the same tick
                // (or a stale summary) left less than promised. Refuse rather than pay for a partial
                // delivery — nothing has been inserted into the box yet.
                return new Result(completed, Result.Reason.STOCK_TOO_LOW);
            }

            box.insert(payment);
            villager.notifyTrade(offer); // grants trade xp and calls offer.increaseUses() itself
            completed++;
        }
    }

    private static boolean matches(MerchantOffer offer, ShopAccess shop) {
        StackShape offerCost = shapeOf(offer.getCostA());
        Optional<StackShape> offerSecondCost = offer.getItemCostB().map(cost -> shapeOf(cost.itemStack()));
        StackShape offerResult = shapeOf(offer.getResult());
        List<StackShape> goods = shop.goods().stream().filter(stack -> !stack.isEmpty()).map(TransactionExecutor::shapeOf).toList();
        StackShape price = shapeOf(shop.price());
        return MatchRule.matches(offerCost, offerSecondCost, offerResult, goods, price);
    }

    /** Draws up to {@code goods.getCount()} matching items from the network's packagers; returns how many were actually drawn. */
    private static int drawFromNetwork(StockTickerBlockEntity ticker, ItemStack goods) {
        UUID freqId = ticker.behaviour.freqId;
        int remaining = goods.getCount();
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
            remaining -= ((ContainerExtension) target).extract(goods, remaining);
        }
        return goods.getCount() - remaining;
    }

    private static StackShape shapeOf(ItemStack stack) {
        return new StackShape(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
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
