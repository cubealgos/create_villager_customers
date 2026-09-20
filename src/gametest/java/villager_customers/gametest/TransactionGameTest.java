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

    /**
     * Every unit's nugget count is the orb roll (3 to 6 xp) divided by 3 xp per nugget, rounded up:
     * always 1 or 2, regardless of the offer's own xp (`TRANSACTION-REQ-010`, VC-12).
     */
    private static final int MIN_NUGGETS_PER_UNIT = 1;
    private static final int MAX_NUGGETS_PER_UNIT = 2;

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
            int nuggetsAfterFirst = nuggetCount(network);
            helper.assertTrue(
                nuggetsAfterFirst >= MIN_NUGGETS_PER_UNIT && nuggetsAfterFirst <= MAX_NUGGETS_PER_UNIT,
                "the payment box holds 1 or 2 xp nuggets (the orb's 3-to-6 xp roll / 3 per nugget, rounded up): " + nuggetsAfterFirst
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
            int nuggetsAfterSecond = nuggetCount(network);
            helper.assertTrue(
                nuggetsAfterSecond >= 2 * MIN_NUGGETS_PER_UNIT && nuggetsAfterSecond <= 2 * MAX_NUGGETS_PER_UNIT,
                "the payment box now holds nuggets for both units (2 to 4): " + nuggetsAfterSecond
            );
            helper.assertTrue(
                villager.getVillagerXp() == 2 * OFFER_XP, "the villager's trade xp was granted a second time: " + villager.getVillagerXp()
            );
            helper.assertEntityNotPresent(EntityTypes.EXPERIENCE_ORB);
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

    /**
     * VC-12: a mod-driven unit spawns no {@code ExperienceOrb} — vanilla's own trade-xp orb is
     * suppressed by {@code VillagerRewardTradeXpMixin} while {@code TransactionExecutor} holds the
     * mod-driven flag — and each unit's payment-box nuggets carry the orb's own roll (3 to 6 xp,
     * always 1 or 2 nuggets at 3 xp each) rather than the offer's own xp, while the villager's
     * levelling xp still rises by the offer's xp every time (`TRANSACTION-REQ-010`).
     *
     * <p>Run one unit at a time across 20 fresh offers (rather than one offer with 20 uses) so each
     * unit's own nugget delta can be checked individually against 1..2, not just the 20-unit total.
     */
    @GameTest(maxTicks = 800)
    public void aModDrivenUnitSpawnsNoOrbAndItsNuggetsCarryTheOrbsRoll(GameTestHelper helper) {
        int units = 20;
        TestShopNetwork network = TestShopNetwork.build(helper, 0);
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(2, () -> {
            int nuggetsBefore = 0;
            for (int unit = 1; unit <= units; unit++) {
                network.setWheat(1); // exactly enough for this one unit
                MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 1), new ItemStack(Items.EMERALD, 1), 1, OFFER_XP, 0.0f);
                ShopAccess shop = new TestShop(
                    List.of(new ItemStack(Items.WHEAT, 1)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
                );

                TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
                helper.assertTrue(result.unitsCompleted() == 1, "unit " + unit + " completed: " + result);

                int nuggetsAfter = nuggetCount(network);
                int nuggetsThisUnit = nuggetsAfter - nuggetsBefore;
                helper.assertTrue(
                    nuggetsThisUnit >= MIN_NUGGETS_PER_UNIT && nuggetsThisUnit <= MAX_NUGGETS_PER_UNIT,
                    "unit " + unit + " inserted 1 or 2 xp nuggets, was " + nuggetsThisUnit
                );
                nuggetsBefore = nuggetsAfter;

                helper.assertTrue(
                    villager.getVillagerXp() == unit * OFFER_XP,
                    "the villager's trade xp rose by the offer's xp on unit " + unit + ": " + villager.getVillagerXp()
                );
            }

            helper.assertEntityNotPresent(EntityTypes.EXPERIENCE_ORB);
            helper.succeed();
        });
    }

    /**
     * VC-12: {@code VillagerRewardTradeXpMixin} scopes its suppression to a mod-driven unit only —
     * with {@code ModDrivenTrade}'s flag not set, calling {@code villager.notifyTrade(offer)} directly
     * (as a real player trade does, never going through {@link TransactionExecutor}) still spawns its
     * vanilla {@code ExperienceOrb}, proving the mixin does not suppress the orb unconditionally.
     */
    @GameTest(maxTicks = 200)
    public void aDirectNotifyTradeOutsideTheExecutorStillSpawnsItsOrb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 1), new ItemStack(Items.EMERALD, 1), 1, OFFER_XP, 0.0f);
        helper.assertTrue(offer.shouldRewardExp(), "the offer is set up to reward xp, so vanilla would spawn an orb for it");

        helper.runAfterDelay(2, () -> {
            villager.notifyTrade(offer);
            helper.assertEntityPresent(EntityTypes.EXPERIENCE_ORB);
            helper.succeed();
        });
    }

    /**
     * VC-13: {@code countSpace(payment)} is only a fast pre-check — it agrees with
     * {@code insert(payment)} bytecode-for-bytecode on a container whose content does not move
     * between the two calls (`javap -p -c` on {@code ContainerMixin}, VC-13 Findings), so a box
     * already too full to hold the whole payment is already caught by that pre-check and never
     * reaches {@code insert} at all. To force the two to disagree the way a real race would — the
     * payment box filling in between the check and the write — this test uses
     * {@link TransactionExecutor#setBeforeInsertHookForTesting} to fill the box down to exactly one
     * free slot right after {@code countSpace} has passed on an empty box: enough room for the
     * price (an emerald) but not for the xp nuggets that must land with it. {@code insert} then
     * places the emerald into the one free slot and returns the nuggets as leftover; the unit must
     * roll the emerald back out again, touch no goods, and report {@code BOX_FULL} — proving the
     * atomicity fix, not just its absence of a crash.
     */
    @GameTest(maxTicks = 200)
    public void aBoxThatFillsBetweenTheSpaceCheckAndTheInsertRollsBackAndRefusesAtomically(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 1); // exactly enough for one unit
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 1), new ItemStack(Items.EMERALD, 1), 1, OFFER_XP, 0.0f);
        ShopAccess shop = new TestShop(
            List.of(new ItemStack(Items.WHEAT, 1)), new ItemStack(Items.EMERALD, 1), network.ticker, network.chestPos
        );

        helper.runAfterDelay(2, () -> {
            TransactionExecutor.setBeforeInsertHookForTesting(() -> fillPaymentBoxLeavingOneFreeSlot(network.ticker));
            TransactionExecutor.Result result;
            try {
                result = TransactionExecutor.execute(level, villager, offer, shop);
            } finally {
                TransactionExecutor.resetBeforeInsertHookForTesting();
            }

            helper.assertTrue(result.unitsCompleted() == 0, "no unit completed: " + result);
            helper.assertTrue(result.reason() == TransactionExecutor.Result.Reason.BOX_FULL, "refused as box full: " + result);
            helper.assertTrue(offer.getUses() == 0, "the offer's uses are untouched: " + offer.getUses());
            helper.assertTrue(villager.getVillagerXp() == 0, "no trade xp was granted: " + villager.getVillagerXp());
            helper.assertTrue(chestWheatCount(network) == 1, "the wheat never left the chest: " + chestWheatCount(network));
            helper.assertTrue(itemCount(network.ticker, Items.EMERALD) == 0, "the rolled-back emerald did not stay in the box");
            helper.assertTrue(itemCount(network.ticker, AllItems.EXP_NUGGET) == 0, "no xp nugget ever landed in the box");
            helper.assertTrue(
                freeSlotCount(network.ticker) == 1, "the box is exactly as the hook left it: still one free slot, nothing else moved"
            );

            helper.succeed();
        });
    }

    /** Fills every slot but the last of {@code ticker}'s payment box with an unrelated stack. */
    private static void fillPaymentBoxLeavingOneFreeSlot(StockTickerBlockEntity ticker) {
        var box = ticker.receivedPayments;
        int size = box.getContainerSize();
        for (int slot = 0; slot < size - 1; slot++) {
            box.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        box.setItem(size - 1, ItemStack.EMPTY);
    }

    private static int freeSlotCount(StockTickerBlockEntity ticker) {
        var box = ticker.receivedPayments;
        int free = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (box.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    static int chestWheatCount(TestShopNetwork network) {
        ItemStack stack = network.chest.getItem(0);
        return stack.is(Items.WHEAT) ? stack.getCount() : 0;
    }

    static boolean paymentBoxHolds(TestShopNetwork network, Item item, int count) {
        return paymentBoxHolds(network.ticker, item, count);
    }

    static boolean paymentBoxHolds(StockTickerBlockEntity ticker, Item item, int count) {
        return itemCount(ticker, item) == count;
    }

    /** How many of {@code item} the payment box holds in total, across every slot. */
    static int itemCount(StockTickerBlockEntity ticker, Item item) {
        var box = ticker.receivedPayments;
        int total = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static int nuggetCount(TestShopNetwork network) {
        return itemCount(network.ticker, AllItems.EXP_NUGGET);
    }

    static int wheatCount(ChestBlockEntity chest) {
        ItemStack stack = chest.getItem(0);
        return stack.is(Items.WHEAT) ? stack.getCount() : 0;
    }
}
