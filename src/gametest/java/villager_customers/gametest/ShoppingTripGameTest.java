package villager_customers.gametest;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.infrastructure.component.AutoRequestData;
import com.zurrtum.create.infrastructure.component.PackageOrderWithCrafts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;
import villager_customers.customer.ShoppingTripBehavior;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * VC-4's four acceptance-criteria game tests: a full trip and trade while in {@code WORK}, no trip
 * at all outside {@code WORK} even with a forced roll, a shop removed mid-walk cancelling cleanly
 * with a cooldown, and the mixin coexisting with a second, independently added {@code WORK}
 * behaviour (`docs/spec/domains/customer.md` `CUSTOMER-REQ-001`..`009`; `ARCH-FAIL-004`;
 * `TEST-REQ-003`).
 *
 * <p>Every test forces the restock roll via {@link CustomerHooks#setRollSourceForTesting} and calls
 * the real {@code Villager.restock()} directly, exercising the actual mixin-hooked production path
 * rather than calling {@code CustomerHooks.onRestock} itself. Distances stay within roughly a dozen
 * blocks of each test's own origin — the same margin {@code ShopSearchGameTest} documents — since
 * Fabric's game test batches place structures only that far apart; the "shop removed mid-walk" test
 * therefore spawns the villager 8 blocks from the shop rather than 20, close enough to still prove a
 * genuine mid-walk cancellation (arrival is within 2 blocks) without risking a structure overrun.
 */
public final class ShoppingTripGameTest {
    @GameTest(maxTicks = 300)
    public void aVillagerInWorkWalksToAMatchingShopAndTrades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20); // enough stock for exactly one unit
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3)); // 4 blocks from the cloth
        villager.getOffers().add(freshOffer());

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the forced roll set a trip target"
            );

            helper.succeedWhen(() -> helper.assertTrue(
                TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 1),
                "the villager walked to the shop and traded: the payment box should hold one emerald"
            ));
        });
    }

    @GameTest(maxTicks = 250)
    public void aVillagerNotInWorkAtNightDoesNotStartATripEvenWithAForcedRoll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20);
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3));
        villager.getOffers().add(freshOffer());

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            helper.setTime(18000); // night
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            helper.assertTrue(!villager.getBrain().isActive(Activity.WORK), "the villager is not in WORK at night");

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.runAfterDelay(200, () -> {
                helper.assertTrue(
                    !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET),
                    "no trip target was remembered while not in WORK, even with a forced roll"
                );
                helper.assertTrue(
                    TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 0), "no trade happened; the payment box is still empty"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 250)
    public void aShopRemovedMidWalkCancelsTheTripClearsTheMemoryAndStartsCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20);
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 9); // 8 blocks from the villager's spawn

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.getOffers().add(freshOffer());
        Vec3 spawnPos = villager.position();

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the forced roll set a trip target"
            );
            long rollTime = level.getGameTime();

            helper.runAfterDelay(40, () -> {
                helper.assertTrue(villager.position().distanceTo(spawnPos) > 0.1, "the villager has moved toward the shop");

                helper.destroyBlock(clothRelative);

                helper.succeedWhen(() -> {
                    helper.assertTrue(
                        !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET),
                        "the trip memory is cleared once the shop loses its shophood mid-walk"
                    );
                    helper.assertTrue(
                        villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_COOLDOWN).filter(cooldown -> cooldown > rollTime).isPresent(),
                        "a cooldown was started after the cancelled trip"
                    );
                    helper.assertTrue(
                        TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 0), "no partial transaction occurred"
                    );
                });
            });
        });
    }

    /**
     * `ARCH-FAIL-004`/`TEST-REQ-003`: {@code Brain.addActivity} is additive, so a second,
     * independently added {@code WORK} behaviour — standing in for another mod's own mixin — must
     * coexist with this mod's own {@link ShoppingTripBehavior}, already added by the real
     * {@code VillagerBrainMixin} at spawn. Read via reflection on {@code Brain}'s private
     * {@code availableBehaviorsByPriority} field, since nothing public exposes a brain's registered
     * behaviour set.
     */
    @GameTest
    public void theMixinCoexistsWithASecondIndependentlyAddedWorkBehaviour(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(2, () -> {
            BehaviorControl<Villager> trivial = new BehaviorControl<>() {
                @Override
                public Behavior.Status getStatus() {
                    return Behavior.Status.STOPPED;
                }

                @Override
                public Set<MemoryModuleType<?>> getRequiredMemories() {
                    return Set.of();
                }

                @Override
                public boolean tryStart(ServerLevel level, Villager entity, long gameTime) {
                    return false;
                }

                @Override
                public void tickOrStop(ServerLevel level, Villager entity, long gameTime) {
                }

                @Override
                public void doStop(ServerLevel level, Villager entity, long gameTime) {
                }

                @Override
                public String debugString() {
                    return "villager_customers-gametest-trivial";
                }
            };

            villager.getBrain().addActivity(Activity.WORK, ImmutableList.of(Pair.of(50, trivial)), Set.of(), Set.of());

            Set<BehaviorControl<? super Villager>> workBehaviors = readWorkBehaviors(villager.getBrain());
            helper.assertTrue(
                workBehaviors.stream().anyMatch(b -> b instanceof ShoppingTripBehavior),
                "this mod's own ShoppingTripBehavior, added by the real mixin at spawn, is present in WORK"
            );
            helper.assertTrue(workBehaviors.contains(trivial), "a second, independently added behaviour is also present in WORK");
            helper.succeed();
        });
    }

    private static void configureCloth(GameTestHelper helper, BlockPos clothRelative, BlockPos tickerRelative) {
        BlockPos clothPos = helper.absolutePos(clothRelative);
        BlockPos tickerPos = helper.absolutePos(tickerRelative);
        TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
        cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
        cloth.priceTag.count = 1;
        cloth.requestData = new AutoRequestData(
            PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.WHEAT), 20))), "", tickerPos.subtract(clothPos), "", true
        );
    }

    private static MerchantOffer freshOffer() {
        return new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
    }

    @SuppressWarnings("unchecked")
    private static Set<BehaviorControl<? super Villager>> readWorkBehaviors(Brain<Villager> brain) {
        try {
            Field field = Brain.class.getDeclaredField("availableBehaviorsByPriority");
            field.setAccessible(true);
            Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>> byPriority =
                (Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>>) field.get(brain);
            Set<BehaviorControl<? super Villager>> all = new HashSet<>();
            for (Map<Activity, Set<BehaviorControl<? super Villager>>> byActivity : byPriority.values()) {
                Set<BehaviorControl<? super Villager>> set = byActivity.get(Activity.WORK);
                if (set != null) {
                    all.addAll(set);
                }
            }
            return all;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
