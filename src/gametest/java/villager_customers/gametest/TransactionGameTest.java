package villager_customers.gametest;

import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import villager_customers.transaction.ShopAccess;
import villager_customers.transaction.TransactionExecutor;

/**
 * VC-3: a matched offer executed against a real chest-and-packager network draws the goods, pays
 * the price and xp nuggets, and advances the offer's uses and the villager's trade xp — one unit
 * per visit while stock and uses allow (`docs/spec/domains/transaction.md` `TRANSACTION-REQ-004`
 * through `TRANSACTION-REQ-007`) — while the offer's demand and the villager's reputation toward a
 * player stay exactly as they were, since a mod-driven unit involves no player (`TRANSACTION-REQ-009`).
 */
public final class TransactionGameTest {
    private static final int OFFER_XP = 10;
    private static final int EXPECTED_NUGGETS = 4; // ceil(10 / 3), TRANSACTION-REQ-010

    @GameTest(maxTicks = 200)
    public void aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 20); // enough stock for exactly one unit
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, OFFER_XP, 0.0f);
        ShopAccess shop = new TestShop(
            List.of(new ItemStack(Items.WHEAT, 20)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
        );

        // TRANSACTION-REQ-009: demand and player reputation are entirely a player-trade concern; a
        // mod-driven unit must never touch either. A non-zero starting reputation (rather than the
        // default zero) makes "unchanged" a real assertion, not a vacuous one.
        Player mockPlayer = helper.makeMockServerPlayer(GameType.SURVIVAL);
        villager.getGossips().add(mockPlayer.getUUID(), GossipType.TRADING, 5);
        int demandBefore = offer.getDemand();
        int reputationBefore = villager.getPlayerReputation(mockPlayer);

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.Result first = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(first.unitsCompleted() == 1, "one unit completed: " + first);
            helper.assertTrue(
                first.reason() == TransactionExecutor.Result.Reason.STOCK_TOO_LOW, "stops once the chest's 20 wheat are gone: " + first
            );
            helper.assertTrue(offer.getUses() == 1, "the offer's uses are 1: " + offer.getUses());
            helper.assertTrue(chestWheatCount(network) == 0, "the 20 wheat left the chest");
            helper.assertTrue(paymentBoxHolds(network, Items.EMERALD, 1), "the payment box holds one emerald");
            helper.assertTrue(
                paymentBoxHolds(network, AllItems.EXP_NUGGET, EXPECTED_NUGGETS),
                "the payment box holds " + EXPECTED_NUGGETS + " xp nuggets (10 xp / 3 per nugget, rounded up)"
            );
            helper.assertTrue(villager.getVillagerXp() == OFFER_XP, "the villager's trade xp was granted once: " + villager.getVillagerXp());
            helper.assertTrue(offer.getDemand() == demandBefore, "the offer's demand is untouched: " + offer.getDemand() + " vs " + demandBefore);
            helper.assertTrue(
                villager.getPlayerReputation(mockPlayer) == reputationBefore,
                "the villager's reputation toward the player is untouched: " + villager.getPlayerReputation(mockPlayer) + " vs " + reputationBefore
            );

            network.setWheat(20); // a second visit finds the chest restocked
            TransactionExecutor.Result second = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(second.unitsCompleted() == 1, "a second unit completed: " + second);
            helper.assertTrue(second.reason() == TransactionExecutor.Result.Reason.OUT_OF_USES, "stops out of uses: " + second);
            helper.assertTrue(offer.getUses() == 2, "the offer's uses are 2: " + offer.getUses());
            helper.assertTrue(paymentBoxHolds(network, Items.EMERALD, 2), "the payment box now holds two emeralds");
            helper.assertTrue(
                paymentBoxHolds(network, AllItems.EXP_NUGGET, 2 * EXPECTED_NUGGETS), "the payment box now holds nuggets for both units"
            );
            helper.assertTrue(
                villager.getVillagerXp() == 2 * OFFER_XP, "the villager's trade xp was granted a second time: " + villager.getVillagerXp()
            );
            helper.assertTrue(
                offer.getDemand() == demandBefore, "the offer's demand is still untouched after a second unit: " + offer.getDemand()
            );
            helper.assertTrue(
                villager.getPlayerReputation(mockPlayer) == reputationBefore,
                "the villager's reputation toward the player is still untouched after a second unit: " + villager.getPlayerReputation(mockPlayer)
            );

            helper.succeed();
        });
    }

    static int chestWheatCount(TestShopNetwork network) {
        ItemStack stack = network.chest.getItem(0);
        return stack.is(Items.WHEAT) ? stack.getCount() : 0;
    }

    static boolean paymentBoxHolds(TestShopNetwork network, Item item, int count) {
        return paymentBoxHolds(network.ticker, item, count);
    }

    static boolean paymentBoxHolds(StockTickerBlockEntity ticker, Item item, int count) {
        var box = ticker.receivedPayments;
        int total = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total == count;
    }

    static int wheatCount(ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(0);
        return stack.is(Items.WHEAT) ? stack.getCount() : 0;
    }
}
