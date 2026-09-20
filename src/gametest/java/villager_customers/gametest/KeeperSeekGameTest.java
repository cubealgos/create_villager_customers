package villager_customers.gametest;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.contraptions.actors.seat.SeatEntity;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import villager_customers.keeper.KeeperHooks;
import villager_customers.keeper.KeeperMemoryModules;

/**
 * `VC-20`'s seven acceptance-criteria game tests for the nitwit keeper seek
 * (`docs/spec/domains/keeper.md` `KEEPER-REQ-003`..`013`): a single adult nitwit claims and seats
 * itself at an eligible seat; two adult nitwits contend for one seat and only one seats; a baby
 * nitwit never seeks; a non-nitwit unemployed villager never seeks; ejection starts the cooldown and
 * the nitwit re-seeks once it elapses; a broken seat unseats it the same way; and no eligible seat
 * anywhere leaves the nitwit idle with no error.
 *
 * <p>Every test places a bare {@code StockTickerBlockEntity} and, where needed, a Create seat at one
 * of {@code isKeeperPresent()}'s own eight candidate offsets (`KeeperSeatSearch`'s own Javadoc) — no
 * table cloth or logistics network is built, since this domain only needs the ticker's own {@code
 * isKeeperPresent()} gate, not a working shop (this ticket's own Scope: "can be built and tested
 * against worldgen nitwits alone").
 *
 * <p>{@code Activity.IDLE} is forced active the same way {@code ShoppingTripGameTest} forces {@code
 * Activity.WORK}, after {@code helper.setTime(2000)} (working hours) rather than before — the same
 * order that class's own farmer test found necessary, since {@code UpdateActivityFromSchedule}'s own
 * throttle stamps its last-checked game time on the very first schedule read, and setting the time
 * first means that stamp already agrees with the forced activity. A nitwit's own {@code WORK}
 * requirement fails structurally (`KeeperSeekBehavior`'s own Javadoc: {@code JOB_SITE} can never be
 * present), so the schedule keeps re-selecting {@code IDLE} on its own once forced, exactly the
 * synergy {@code ARCH-DEC-006} relies on.
 *
 * <p>{@link KeeperHooks#clearClaimsForTesting()} runs at the start of every test that cares about
 * deterministic claiming, since {@code SeatClaims}' own map is one static instance shared by the
 * whole game-test JVM run, not reset between tests on its own. Deliberately <em>not</em> scoped:
 * {@code shop_search_radius} itself ({@code VillagerCustomersConfig}) — that field is shared by every
 * concurrently running game test in this suite's own batch (found the hard way: an earlier version of
 * this class scoped it down per test and broke {@code VillageWideShopSearchGameTest}, running in the
 * same batch, out from under it). Each test's own villager spawns only a few blocks from its own
 * ticker/seat, always nearer than any sibling {@code KeeperSeekGameTest} structure's own seat could
 * be even at the full 128-block default, so {@code KeeperSeatSearch}'s own "nearest first" ordering
 * (`KEEPER-REQ-007`) always resolves to the seat this test itself built.
 */
public final class KeeperSeekGameTest {
    @GameTest(maxTicks = 300)
    public void anAdultNitwitClaimsAndSeatsAtAnEligibleSeat(GameTestHelper helper) {
        KeeperHooks.clearClaimsForTesting();
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos seatRelative = tickerRelative.east(1); // isKeeperPresent()'s own offset-0/EAST candidate
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(seatRelative, AllBlocks.SEAT.white());

        Villager villager = spawnAdultNitwit(helper, new BlockPos(1, 2, 1)); // 4 blocks from the ticker

        helper.runAfterDelay(3, () -> {
            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

            StockTickerBlockEntity ticker = helper.getBlockEntity(tickerRelative, StockTickerBlockEntity.class);
            helper.assertTrue(!ticker.isKeeperPresent(), "the ticker starts with no keeper");

            helper.succeedWhen(() -> {
                helper.assertTrue(villager.getVehicle() instanceof SeatEntity, "the nitwit ended up seated");
                helper.assertTrue(ticker.isKeeperPresent(), "the ticker now reports a keeper present");
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void twoNitwitsContendForOneSeatOnlyOneSeats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KeeperHooks.clearClaimsForTesting();

        Villager villagerA = spawnAdultNitwit(helper, new BlockPos(1, 2, 1));
        Villager villagerB = spawnAdultNitwit(helper, new BlockPos(3, 2, 1));

        // Built far from this test's own structure and force-loaded (`noEligibleSeatAnywhere...`'s
        // own Approach), the only seat within shop_search_radius of either villager: a losing claim
        // must genuinely find nothing else to fall back to, or it just walks off to whatever sibling
        // KeeperSeekGameTest structure's own seat is next-nearest instead — `KeeperSeatSearch`'s own
        // "try the next-nearest unclaimed seat" behaviour is correct in a real village with more than
        // one shop, but made this test's own local seat ambiguous with every other one this suite
        // places (found the hard way, this ticket's own game-test run).
        // -200/-200, the opposite direction from noEligibleSeatAnywhereLeavesTheNitwitIdleWithNoError's
        // own +200/+200 (this class's own sibling test structures sit only tens of blocks apart, so
        // the same offset in the same direction would have put both tests' own far points within
        // shop_search_radius of *each other* — found the hard way, this ticket's own game-test run;
        // 200, not `VillageWideShopSearchGameTest`'s own 100, clears its own 128-block search radius
        // with margin while staying within that class's own proven-safe far-placement range).
        BlockPos farTickerPos = villagerA.blockPosition().offset(-200, -1, -200);
        BlockPos farSeatPos = farTickerPos.east(1);
        forceLoad(level, farTickerPos, true);
        level.setBlock(farTickerPos, AllBlocks.STOCK_TICKER.defaultBlockState(), 3);
        level.setBlock(farSeatPos, AllBlocks.SEAT.white().defaultBlockState(), 3);

        // Positioned and forced into IDLE immediately, before either villager's very first tick can
        // run at all (`noEligibleSeatAnywhereLeavesTheNitwitIdleWithNoError`'s own Findings: a fresh
        // villager's Activity.IDLE is already active by default, so a seek at the *original* spawn
        // position — with nothing built nearby there in this test — would otherwise fire first,
        // find nothing, and set a real 24000-tick cooldown well before this method's own 3-tick delay
        // ever ran, silently blocking either villager from ever reaching the seat built below).
        villagerA.setPos(farTickerPos.getX() - 2, farTickerPos.getY() + 1, farTickerPos.getZ() + 0.5);
        villagerB.setPos(farTickerPos.getX() + 3, farTickerPos.getY() + 1, farTickerPos.getZ() + 0.5);
        helper.setTime(2000);
        villagerA.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        villagerB.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        helper.runAfterDelay(3, () -> {
            helper.succeedWhen(() -> {
                boolean aSeated = villagerA.getVehicle() instanceof SeatEntity;
                boolean bSeated = villagerB.getVehicle() instanceof SeatEntity;
                helper.assertTrue(aSeated ^ bSeated, "exactly one of the two nitwits is seated: A=" + aSeated + " B=" + bSeated);
                Villager loser = aSeated ? villagerB : villagerA;
                helper.assertTrue(
                    loser.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(),
                    "the losing nitwit's claim attempt failed and left no claimed-seat memory behind"
                );
                forceLoad(level, farTickerPos, false);
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void aBabyNitwitNeverSeeks(GameTestHelper helper) {
        KeeperHooks.clearClaimsForTesting();
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos seatRelative = tickerRelative.east(1);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(seatRelative, AllBlocks.SEAT.white());

        Villager villager = spawnAdultNitwit(helper, new BlockPos(1, 2, 1));
        villager.setBaby(true);

        helper.runAfterDelay(3, () -> {
            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(
                    villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(), "a baby nitwit never claims a seat"
                );
                helper.assertTrue(!(villager.getVehicle() instanceof SeatEntity), "a baby nitwit never ends up seated");
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void aNonNitwitUnemployedVillagerNeverSeeks(GameTestHelper helper) {
        KeeperHooks.clearClaimsForTesting();
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos seatRelative = tickerRelative.east(1);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(seatRelative, AllBlocks.SEAT.white());

        // A freshly spawned villager already carries profession NONE (`KEEPER-REQ-014`'s own
        // "unemployed" villager, distinct from this domain's use of "idle" for a nitwit with no
        // active claim); no profession is set here at all.
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(3, () -> {
            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(
                    villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(),
                    "an unemployed, non-nitwit villager never claims a seat"
                );
                helper.assertTrue(!(villager.getVehicle() instanceof SeatEntity), "an unemployed, non-nitwit villager never ends up seated");
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 200)
    public void ejectionStartsTheCooldownThenTheNitwitReSeeks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KeeperHooks.clearClaimsForTesting();

        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos seatRelative = tickerRelative.east(1);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(seatRelative, AllBlocks.SEAT.white());

        Villager villager = spawnAdultNitwit(helper, new BlockPos(1, 2, 1));

        helper.runAfterDelay(3, () -> {
            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

            BlockPos seatPos = helper.absolutePos(seatRelative);
            forceSeat(helper, villager, seatPos);

            helper.runAfterDelay(2, () -> {
                helper.assertTrue(villager.getVehicle() instanceof SeatEntity, "the nitwit starts this test already seated");

                villager.stopRiding(); // simulates a player's right-click eviction (`SeatBlock.useItemOn`)

                // A few ticks for KeeperSeekBehavior's own reconciliation (checkExtraStartConditions)
                // to notice the ejection, release the claim and start the real production cooldown
                // (`keeper_seek_cooldown_ticks`, 24000 by default) — deliberately not shortened via
                // VillagerCustomersConfig here (found the hard way, this ticket's own game-test run:
                // that field is shared by every concurrently running test in this suite's own batch,
                // and a shortened cooldown left over from this very test corrupted a sibling KEEPER
                // test's own "no premature retry" expectations). Once reconciled, this one villager's
                // own cooldown memory is set directly to "already elapsed" instead — per-entity state,
                // touching nothing shared.
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(
                        villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(),
                        "the ejection was reconciled: the claim was released"
                    );
                    villager.getBrain().setMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN, level.getGameTime());

                    helper.succeedWhen(() -> helper.assertTrue(
                        villager.getVehicle() instanceof SeatEntity, "the nitwit re-seated itself once its own cooldown had elapsed"
                    ));
                });
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void aBrokenSeatUnseatsTheNitwitTheSameWayAsEjection(GameTestHelper helper) {
        KeeperHooks.clearClaimsForTesting();
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos seatRelative = tickerRelative.east(1);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(seatRelative, AllBlocks.SEAT.white());

        Villager villager = spawnAdultNitwit(helper, new BlockPos(1, 2, 1));

        helper.runAfterDelay(3, () -> {
            helper.setTime(2000);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

            BlockPos seatPos = helper.absolutePos(seatRelative);
            forceSeat(helper, villager, seatPos);

            helper.runAfterDelay(2, () -> {
                helper.assertTrue(villager.getVehicle() instanceof SeatEntity, "the nitwit starts this test already seated");

                helper.destroyBlock(seatRelative);

                helper.succeedWhen(() -> {
                    helper.assertTrue(!(villager.getVehicle() instanceof SeatEntity), "breaking the seat unseated the nitwit");
                    helper.assertTrue(
                        villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(),
                        "the claim was released once the break was noticed"
                    );
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(
                        villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN).filter(cooldown -> cooldown > now - 1000).isPresent(),
                        "a cooldown was started the same way ejection starts one"
                    );
                });
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void noEligibleSeatAnywhereLeavesTheNitwitIdleWithNoError(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KeeperHooks.clearClaimsForTesting();
        Villager villager = spawnAdultNitwit(helper, new BlockPos(1, 2, 1));

        // Moved far from this test's own (empty) structure immediately, before this behaviour's own
        // first tick can run at all — the same "place far away and force-load" pattern
        // `VillageWideShopSearchGameTest` uses: at the real production radius (128 blocks by
        // default), staying inside this small structure even for the first couple of ticks risked a
        // seek claiming a seat from one of this suite's own sibling KeeperSeekGameTest structures
        // before ever reaching the code below that moves it away (found the hard way, this ticket's
        // own game-test run: a fresh villager's Activity.IDLE is already active by default, with
        // nothing needing this class's usual 3-tick delay for a block entity to settle).
        BlockPos farPos = villager.blockPosition().offset(200, 0, 200);
        forceLoad(level, farPos, true);
        villager.setPos(farPos.getX() + 0.5, farPos.getY(), farPos.getZ() + 0.5);
        helper.setTime(2000);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT).isEmpty(),
                "no eligible seat anywhere means no claim was ever made"
            );
            helper.assertTrue(!(villager.getVehicle() instanceof SeatEntity), "the nitwit never ended up seated");
            helper.assertTrue(
                villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN).isPresent(),
                "the seek still ran (and found nothing) rather than erroring or never trying at all"
            );
            forceLoad(level, farPos, false);
            helper.succeed();
        });
    }

    /** Forces or releases a chunk-loading ticket over {@code pos}'s chunk and its 8 neighbours (`VillageWideShopSearchGameTest`'s own Approach). */
    private static void forceLoad(ServerLevel level, BlockPos pos, boolean forced) {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(chunkPos.x() + dx, chunkPos.z() + dz, forced);
            }
        }
    }

    /** An adult, non-baby villager with profession {@code NITWIT} (`KEEPER-REQ-001`-shaped, without the breeding roll VC-19 owns). */
    private static Villager spawnAdultNitwit(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, relative);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));
        return villager;
    }

    /**
     * Seats {@code villager} on the {@code SeatBlock} at {@code seatPos} and sets its own {@code
     * KEEPER_CLAIMED_SEAT} memory directly, bypassing the walk — this ticket's own game tests only
     * need to prove the reconciliation logic ({@code KeeperSeekBehavior#checkExtraStartConditions})
     * that runs once a claim is found orphaned, not re-prove the walk itself
     * ({@code anAdultNitwitClaimsAndSeatsAtAnEligibleSeat} already does that end to end).
     */
    private static void forceSeat(GameTestHelper helper, Villager villager, BlockPos seatPos) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getBlockState(seatPos).getBlock() instanceof SeatBlock, "forceSeat's own seatPos must be a real SeatBlock");
        SeatBlock.sitDown(level, seatPos, villager);
        villager.getBrain().setMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT, GlobalPos.of(level.dimension(), seatPos));
    }
}
