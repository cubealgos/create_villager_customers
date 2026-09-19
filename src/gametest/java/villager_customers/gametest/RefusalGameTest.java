package villager_customers.gametest;

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
}
