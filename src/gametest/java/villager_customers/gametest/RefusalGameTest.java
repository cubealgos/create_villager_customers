package villager_customers.gametest;

import com.zurrtum.create.content.logistics.packager.InventorySummary;
import com.zurrtum.create.infrastructure.items.ContainerExtension;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.transaction.ShopAccess;
import villager_customers.transaction.TransactionExecutor;

/**
 * VC-3: the three ways a unit is refused before anything moves — insufficient network stock, a
 * full payment box, and an offer that never matches at all (`docs/spec/domains/transaction.md`
 * `TRANSACTION-FAIL-001`, `TRANSACTION-FAIL-002`, `TRANSACTION-FAIL-003`).
 */
public final class RefusalGameTest {
    @GameTest(maxTicks = 200)
    public void stockTooLowRefusesTheUnitAndMovesNothing(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 10); // less than the 20 the offer needs
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
        ShopAccess shop = new TestShop(
            List.of(new ItemStack(Items.WHEAT, 20)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
        );

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(result.reason() == TransactionExecutor.Result.Reason.STOCK_TOO_LOW, "refused as stock too low: " + result);
            helper.assertTrue(TransactionGameTest.chestWheatCount(network) == 10, "the chest's 10 wheat were not touched");
            helper.assertTrue(offer.getUses() == 0, "the offer's uses are untouched: " + offer.getUses());
            helper.assertTrue(TransactionGameTest.paymentBoxHolds(network, Items.EMERALD, 0), "the payment box received nothing");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aFullPaymentBoxRefusesTheUnitAndMovesNothing(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 64); // plenty of stock
        network.fillPaymentBoxWithJunk();
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
        ShopAccess shop = new TestShop(
            List.of(new ItemStack(Items.WHEAT, 20)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
        );

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(result.reason() == TransactionExecutor.Result.Reason.BOX_FULL, "refused as cash register full: " + result);
            helper.assertTrue(TransactionGameTest.chestWheatCount(network) == 64, "the chest's wheat was not touched");
            helper.assertTrue(offer.getUses() == 0, "the offer's uses are untouched: " + offer.getUses());
            helper.assertTrue(TransactionGameTest.paymentBoxHolds(network, Items.EMERALD, 0), "no emerald reached the full box");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void anOfferWithASecondCostNeverMatchesAndMovesNothing(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 64);
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(
            new ItemCost(Items.WHEAT, 20), Optional.of(new ItemCost(Items.STICK, 1)), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f
        );
        ShopAccess shop = new TestShop(
            List.of(new ItemStack(Items.WHEAT, 20)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
        );

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(result.reason() == TransactionExecutor.Result.Reason.NO_MATCH, "never a candidate: " + result);
            helper.assertTrue(TransactionGameTest.chestWheatCount(network) == 64, "the chest's wheat was not touched");
            helper.assertTrue(offer.getUses() == 0, "the offer's uses are untouched: " + offer.getUses());
            helper.succeed();
        });
    }

    /**
     * VC-3 review finding: a short draw (the stock summary said enough was available, but the real
     * network delivered less) must return every already-extracted stack to the exact container it
     * came from, not lose it. Two chests, 15 wheat each (30 total, enough by the summary's word), an
     * offer needing 20 — but the summary is made to lie by reading it once to warm its per-tick
     * cache, then drawing 12 wheat out of chest A directly (a real extraction, not a test-only seam)
     * before {@code execute} ever runs: the stale cached summary still reports 30 available, the real
     * draw only finds 18, and the shortfall must be returned to chest A and chest B respectively.
     */
    @GameTest(maxTicks = 200)
    public void aShortDrawReturnsEveryStackToItsOwnChestAndMovesNothing(GameTestHelper helper) {
        TestShopNetwork.TwoSourceNetwork network = TestShopNetwork.buildTwoSources(helper, 15);
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
        ShopAccess shop = new TestShop(List.of(new ItemStack(Items.WHEAT, 20)), new ItemStack(Items.EMERALD, 1), network.ticker(), BlockPos.ZERO);

        helper.runAfterDelay(2, () -> {
            // Warm the accurate-summary cache (a 1-game-tick TickBasedCache in LogisticsManager)
            // while both chests still hold their full 15 wheat: 30 available, comfortably over 20.
            InventorySummary warm = network.ticker().getAccurateSummary();
            helper.assertTrue(warm.getCountOf(new ItemStack(Items.WHEAT)) == 30, "the warmed summary sees all 30 wheat: " + warm.getCountOf(new ItemStack(Items.WHEAT)));

            // A real extraction directly against chest A's container, within the same game tick as
            // the warm-up above: the cached summary TransactionExecutor reads next is now stale.
            int pulled = ((ContainerExtension) network.chestA()).extract(new ItemStack(Items.WHEAT), 12);
            helper.assertTrue(pulled == 12, "chest A gave up 12 wheat directly: " + pulled);
            helper.assertTrue(TransactionGameTest.wheatCount(network.chestA()) == 3, "chest A now holds 3 wheat");

            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(result.reason() == TransactionExecutor.Result.Reason.STOCK_TOO_LOW, "refused as stock too low: " + result);
            helper.assertTrue(offer.getUses() == 0, "the offer's uses are untouched: " + offer.getUses());

            // Exactly what each chest held right before execute() ran: chest A's 3 (after our own
            // direct pull above) and chest B's full 15 — the 18 wheat the draw did manage to extract
            // across both chests must have been returned to its own chest, not merged or dropped.
            helper.assertTrue(TransactionGameTest.wheatCount(network.chestA()) == 3, "chest A ends with its 3 wheat: " + TransactionGameTest.wheatCount(network.chestA()));
            helper.assertTrue(TransactionGameTest.wheatCount(network.chestB()) == 15, "chest B ends with its 15 wheat: " + TransactionGameTest.wheatCount(network.chestB()));
            helper.assertTrue(TransactionGameTest.paymentBoxHolds(network.ticker(), Items.EMERALD, 0), "the payment box received nothing");
            helper.succeed();
        });
    }
}
