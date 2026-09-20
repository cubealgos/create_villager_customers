package villager_customers.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import villager_customers.transaction.ShopAccess;
import villager_customers.transaction.TransactionExecutor;

/**
 * `VC-15`: the match honours the offer's cost predicate — `ItemCost.components`, a
 * {@code DataComponentExactPredicate} — on top of the plain (item id, count) shape match, and the
 * per-unit draw only ever takes stock that satisfies it (`docs/spec/domains/transaction.md`
 * `TRANSACTION-REQ-011`; `create_firearms` `decisions/DEC-016-villager-customers-requirement.md`).
 *
 * <p>Every test here uses {@code minecraft:custom_name} as the one component the offer's cost
 * predicate expects — {@code ItemCost.test(ItemStack)} checks the item id and
 * {@code DataComponentExactPredicate.test}, confirmed via {@code javap -p -c} on the 26.2 jar to
 * require an exact value match per expected component, so any single vanilla component the test can
 * set exercises the same code path a firearm's attachment predicate would.
 */
public final class ComponentPredicateGameTest {
    private static final Component GOLDEN_WHEAT = Component.literal("Golden Wheat");

    /** An offer whose cost is one paper, named exactly {@link #GOLDEN_WHEAT}. */
    private static MerchantOffer namedPaperOffer() {
        ItemCost cost = new ItemCost(Items.PAPER, 1).withComponents(builder -> builder.expect(DataComponents.CUSTOM_NAME, GOLDEN_WHEAT));
        return new MerchantOffer(cost, new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
    }

    private static ItemStack namedPaper() {
        ItemStack stack = new ItemStack(Items.PAPER, 1);
        stack.set(DataComponents.CUSTOM_NAME, GOLDEN_WHEAT);
        return stack;
    }

    private static ItemStack unnamedPaper() {
        return new ItemStack(Items.PAPER, 1);
    }

    @GameTest(maxTicks = 200)
    public void anOfferWithAComponentPredicateMatchesAGoodsStackThatSatisfiesIt(GameTestHelper helper) {
        ItemStack goods = namedPaper();
        TestShopNetwork network = TestShopNetwork.build(helper, goods.copy());
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = namedPaperOffer();
        ShopAccess shop = new TestShop(List.of(goods.copy()), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos);

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(
                result.unitsCompleted() == 1, "the goods stack satisfying the offer's component predicate completed a unit: " + result
            );
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void anOfferWithAComponentPredicateRefusesAGoodsStackThatDoesNotSatisfyIt(GameTestHelper helper) {
        ItemStack goods = unnamedPaper(); // same item and count, but carries no custom_name at all
        TestShopNetwork network = TestShopNetwork.build(helper, goods.copy());
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = namedPaperOffer();
        ShopAccess shop = new TestShop(List.of(goods.copy()), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos);

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(
                result.reason() == TransactionExecutor.Result.Reason.NO_MATCH,
                "refused as no match, since the shop's goods stack fails the offer's component predicate: " + result
            );
            helper.assertTrue(chestPaperCount(network.chest) == 1, "the chest's unnamed paper was never touched");
            helper.succeed();
        });
    }

    /**
     * Two sources in the same network hold two differently-named paper stacks; the shop's configured
     * goods is the one that satisfies the offer's predicate. A unit must draw only that source's
     * stack and leave the other source's differently-named stack exactly as it was — proving the draw
     * itself, not just the match check, honours the predicate.
     */
    @GameTest(maxTicks = 200)
    public void aUnitDrawsOnlyTheGoodsStackThatSatisfiesTheOffersComponentPredicate(GameTestHelper helper) {
        ItemStack matchingGoods = namedPaper();
        ItemStack otherGoods = new ItemStack(Items.PAPER, 1);
        otherGoods.set(DataComponents.CUSTOM_NAME, Component.literal("Not It"));

        TestShopNetwork.TwoSourceNetwork network = TestShopNetwork.buildTwoSources(helper, matchingGoods.copy(), otherGoods.copy());
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = namedPaperOffer();
        ShopAccess shop = new TestShop(List.of(matchingGoods.copy()), new ItemStack(Items.EMERALD, 1), network.ticker(), BlockPos.ZERO);

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 1, "one unit completed: " + result);

            ItemStack chestAItem = network.chestA().getItem(0);
            helper.assertTrue(chestAItem.isEmpty(), "the matching-named paper was drawn out of its own chest: " + chestAItem);

            ItemStack chestBItem = network.chestB().getItem(0);
            helper.assertTrue(chestBItem.is(Items.PAPER) && chestBItem.getCount() == 1, "the other chest's paper is still there");
            helper.assertTrue(
                chestBItem.has(DataComponents.CUSTOM_NAME) && chestBItem.get(DataComponents.CUSTOM_NAME).equals(Component.literal("Not It")),
                "the other chest's differently-named paper was left completely untouched: " + chestBItem
            );

            helper.succeed();
        });
    }

    private static int chestPaperCount(ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(0);
        return stack.is(Items.PAPER) ? stack.getCount() : 0;
    }
}
