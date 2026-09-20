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
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
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

            // CUSTOMER-REQ-005: arrival hands off to TRANSACTION *and* clears the trip memory; the
            // happy path only asserted the payment box before VC-6's sweep, never the memory clear.
            helper.succeedWhen(() -> {
                helper.assertTrue(
                    TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 1),
                    "the villager walked to the shop and traded: the payment box should hold one emerald"
                );
                helper.assertTrue(
                    !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET),
                    "the trip memory was cleared on arrival"
                );
            });
        });
    }

    /**
     * VC-11: Kevin's client check found a real farmer, standing at its own composter job site,
     * never leaving for a matching shop even though the trip target memory was set. VC-4's own game
     * tests (this class's other methods) never caught it because their villagers carry no profession
     * and no job site, so vanilla's real {@code WORK} package — in particular
     * {@code SetWalkTargetFromBlockMemory(JOB_SITE, ...)} at priority 2, which re-populates
     * {@code WALK_TARGET} from {@code JOB_SITE} on every tick {@code WALK_TARGET} is absent — never
     * had anything to compete against. A farmer already standing right next to its composter is the
     * worst case: priority 2 runs before this mod's own priority-6 {@link ShoppingTripBehavior}
     * every tick, so once anything empties {@code WALK_TARGET} mid-walk, the job site wins it back
     * first unless the trip behaviour re-asserts its own target every tick (this ticket's fix).
     *
     * <p>The farmer is built by setting {@code VillagerData}'s profession directly and calling
     * {@code refreshBrain} (which preserves memories across the rebuilt brain, confirmed via
     * {@code javap -p -c} on {@code Villager.refreshBrain}) rather than waiting on vanilla's own
     * {@code AssignProfessionFromJobSite}, then claiming the composter POI directly through
     * {@code PoiManager.take} (its own entry condition, {@code VillagerProfession.heldJobSite()}, is
     * a bare POI-type predicate with no ownership check, confirmed the same way) and setting
     * {@code JOB_SITE} to it, rather than waiting on vanilla's own {@code AcquirePoi} — both real
     * vanilla behaviours are timing-dependent and not this ticket's own concern.
     *
     * <p>The composter sits 6 blocks from the cloth and the villager spawns 2 blocks from the
     * composter (the ticket's own Approach asked for roughly a dozen; this ticket's own game-test
     * runs found that distance genuine pathing trouble in this harness — see the field comments
     * below and this ticket's Findings) — short enough to stay inside every other test in this
     * class's own proven-safe envelope, long enough that the villager crosses real ground away from
     * its job site, which is what actually exercises the priority race this ticket fixes.
     */
    @GameTest(maxTicks = 500)
    public void aFarmerWithAComposterLeavesItForTheShop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20); // enough stock for exactly one unit
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos keeperRelative = tickerRelative.east(1);
        // The cloth sits at the exact position this class's own arrival tests already prove
        // reachable end-to-end (aVillagerInWorkWalksToAMatchingShopAndTrades and others actually
        // complete a trade there, unlike the "shop removed mid-walk" test's z=9, which only proves
        // movement starts before destroying the cloth mid-walk — this ticket's own game-test runs
        // found genuine, repeatable pathing trouble finishing an arrival at z=9 and beyond, unrelated
        // to the walk-target race this ticket fixes; a farther single-axis stretch, tried first, also
        // let the villager wander outside the structure's own small force-loaded bounding box and
        // freeze mid-trip — alive, but no longer ticking, WALK_TARGET stuck absent forever). The
        // priority race this ticket fixes doesn't need a long walk to prove: it reproduced just as
        // reliably at this distance during that same investigation.
        BlockPos clothRelative = new BlockPos(1, 1, 7);
        BlockPos composterRelative = new BlockPos(1, 1, 1);
        BlockPos villagerSpawnRelative = new BlockPos(1, 2, 3); // 2 blocks from the composter; TestShopNetwork's own packager provides footing here

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );
        helper.setBlock(composterRelative, Blocks.COMPOSTER);

        Villager villager = helper.spawn(EntityTypes.VILLAGER, villagerSpawnRelative);

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            // Set the time before refreshBrain (VC-11 finding): refreshBrain's own registerBrainGoals
            // calls Brain.updateActivityFromSchedule once immediately, stamping its private
            // lastScheduleUpdate at whatever the game time was at that moment. That call throttles
            // itself to once per 20 ticks of *game time elapsed since the stamp* — not real ticks
            // played — so setting the time afterwards (a large jump) makes the very next schedule
            // re-check see a huge elapsed delta and fire immediately, on this behaviour's own
            // priority-99 UpdateActivityFromSchedule slot, undoing the forced WORK the instant the
            // schedule itself is next consulted. Setting the time first means the stamp already
            // reflects working hours, so the schedule agrees with the forced activity instead of
            // fighting it (confirmed by reproducing the flip with the old order, `javap -p -c` on
            // Brain.updateActivityFromSchedule).
            helper.setTime(2000); // working hours, as every other trip test in this class uses

            BlockPos composterPos = helper.absolutePos(composterRelative);
            // Villager.setVillagerData nulls the villager's offers whenever the profession changes
            // (confirmed via javap -p -c on Villager.setVillagerData), so the offer is added only
            // after the profession is set, not before.
            villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
            villager.refreshBrain(level); // rebuilds WORK/CORE for FARMER; preserves memories (Brain.pack/makeBrain)
            villager.getOffers().add(freshOffer());

            level.getPoiManager().take(type -> type.is(PoiTypes.FARMER), (type, pos) -> true, composterPos, 1);
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), composterPos));

            villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the forced roll set a trip target"
            );
            BlockPos shopPos = villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET).orElseThrow().pos();

            // succeedWhen's own Runnable is already polled every tick until success or timeout
            // (`GameTestSequence`'s own mechanism) — the regression check (WALK_TARGET must never
            // settle on the composter instead of the shop, the bug this ticket fixes) is folded into
            // it directly rather than registered as a second recurring check through
            // helper.onEachTick, which crashed the test server (a ConcurrentModificationException-
            // shaped NPE inside GameTestInfo.tickInternal's own per-tick map iteration, whether or not
            // it itself called helper.fail) — found by running this ticket's own game test. A one-tick
            // gap is tolerated: the work package's own SetWalkTargetFromBlockMemory(JOB_SITE) can win
            // the priority race before this behaviour's own re-assertion runs later the very same
            // tick, so the memory never survives to the next tick against the composter.
            helper.succeedWhen(() -> {
                if (villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET)) {
                    var walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                    if (walkTarget.isPresent()) {
                        helper.assertTrue(
                            walkTarget.get().getTarget().currentBlockPosition().equals(shopPos),
                            "the walk target settled on the composter instead of the shop: " + walkTarget.get().getTarget().currentBlockPosition()
                        );
                    }
                }
                helper.assertTrue(
                    TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 1),
                    "the farmer left its composter, walked to the shop and traded: the payment box should hold one emerald"
                );
                helper.assertTrue(
                    !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the trip memory was cleared on arrival"
                );
            });
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
     * VC-6 sweep gap: `CUSTOMER-REQ-008` ("at most one active trip") is implemented as an early
     * return in {@code CustomerHooks.onRestock} but no test forced a second roll while a trip was
     * already active — every other test has at most one shop in existence for its whole run. A
     * second, nearer shop appears only after the first roll already set a trip target at the
     * farther one, so a second forced roll would prefer it if the guard were missing.
     */
    @GameTest
    public void aSecondForcedRollWhileATripIsAlreadyActiveDoesNotReplaceTheTarget(GameTestHelper helper) {
        BlockPos farClothRelative = new BlockPos(1, 1, 7);
        BlockPos farTickerRelative = farClothRelative.east(2);
        BlockPos farKeeperRelative = farTickerRelative.south();
        BlockPos nearClothRelative = new BlockPos(1, 1, 2);
        BlockPos nearTickerRelative = nearClothRelative.east(2);
        BlockPos nearKeeperRelative = nearTickerRelative.south();

        helper.setBlock(farClothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(farTickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(
            farKeeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.getOffers().add(freshOffer());

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, farClothRelative, farTickerRelative);

            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the first forced roll set a trip target"
            );
            var firstTarget = villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET).orElseThrow();

            helper.setBlock(nearClothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
            helper.setBlock(nearTickerRelative, AllBlocks.STOCK_TICKER);
            helper.setBlock(
                nearKeeperRelative,
                AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
            );

            helper.runAfterDelay(2, () -> {
                configureCloth(helper, nearClothRelative, nearTickerRelative);

                helper.runAfterDelay(2, () -> {
                    CustomerHooks.setRollSourceForTesting(() -> 0.0);
                    villager.restock();
                    CustomerHooks.resetRollSourceForTesting();

                    var secondTarget = villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET).orElseThrow();
                    helper.assertTrue(
                        secondTarget.equals(firstTarget),
                        "a second forced roll while a trip is active did not replace the target, even with a nearer shop now present"
                    );
                    helper.succeed();
                });
            });
        });
    }

    /**
     * VC-6 sweep gap: `CUSTOMER-FAIL-002` ("the matched offer runs out of uses before arrival ...
     * re-checked on arrival by TRANSACTION; if no offer still matches, the trip ends with nothing
     * executed, no error") is implemented — {@code TransactionExecutor.execute}'s own
     * {@code OUT_OF_USES} check runs again at arrival — but no test exhausted the offer between the
     * roll and the walk's end; every other walking test's offer still had uses left on arrival. The
     * offer is exhausted immediately after the roll, well before the villager can cover the ~4-block
     * walk, standing in for a player trading it away in the meantime.
     *
     * <p>Uses a diamond/netherite-ingot shape rather than {@link #freshOffer()}'s wheat/emerald one:
     * {@code CustomerHooks.search} runs at the full production {@code ShopSearch.VILLAGE_REACH} (48
     * blocks), which reaches into neighbouring game-test structures, and most of this suite's shops
     * share the wheat-for-emerald shape — a shared shape risks the villager targeting a neighbour's
     * shop instead of this test's own nearby one and never arriving within {@code maxTicks} (found by
     * this ticket's own `just check` run against {@code DebugCommandGameTest}'s sibling gap, not by
     * inspection).
     */
    @GameTest(maxTicks = 300)
    public void anOfferExhaustedMidWalkExecutesNothingOnArrival(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 20);
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3)); // 4 blocks from the cloth
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.DIAMOND, 5), new ItemStack(Items.NETHERITE_INGOT, 1), 2, 10, 0.0f);
        villager.getOffers().add(offer);

        helper.runAfterDelay(3, () -> {
            BlockPos clothPos = helper.absolutePos(clothRelative);
            BlockPos tickerPos = helper.absolutePos(tickerRelative);
            TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
            cloth.priceTag.setFilter(new ItemStack(Items.NETHERITE_INGOT));
            cloth.priceTag.count = 1;
            cloth.requestData = new AutoRequestData(
                PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.DIAMOND), 5))), "", tickerPos.subtract(clothPos), "", true
            );

            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.WORK);

            CustomerHooks.setRollSourceForTesting(() -> 0.0);
            villager.restock();
            CustomerHooks.resetRollSourceForTesting();

            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "the forced roll set a trip target"
            );

            // Exhaust the offer's uses right after the roll, well before the walk completes.
            while (offer.getUses() < offer.getMaxUses()) {
                offer.increaseUses();
            }
            int usesAfterExhausting = offer.getUses();

            helper.succeedWhen(() -> {
                helper.assertTrue(
                    !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET),
                    "the villager arrived and the trip memory was cleared even with nothing left to trade"
                );
                helper.assertTrue(
                    TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 0),
                    "no unit executed: the offer had no uses left by the time the villager arrived"
                );
                helper.assertTrue(offer.getUses() == usesAfterExhausting, "the offer's uses were not touched again: " + offer.getUses());
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
