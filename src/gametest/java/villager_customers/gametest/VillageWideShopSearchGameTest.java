package villager_customers.gametest;

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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import villager_customers.customer.CustomerHooks;
import villager_customers.model.CustomerRules;
import villager_customers.shop.Shop;
import villager_customers.shop.ShopSearch;

import java.util.List;
import java.util.Optional;

/**
 * `VC-18`'s three acceptance-criteria game tests for the search origin
 * (`docs/spec/decisions/DEC-010-village-wide-shop-search.md`, `CUSTOMER-REQ-003`): a shop 100
 * blocks from the villager's meeting point is found when the villager stands at that point; a shop
 * 100 blocks from the villager but 200 from its meeting point is not; and a villager with no
 * meeting point searches from its own position. Each test calls {@link CustomerHooks#search}
 * directly, the same read-only entry point {@code villager_customers.debug.DebugCommand}'s
 * {@code search} subcommand uses — proving the search itself, not the walk, which
 * `docs/spec/operations/testing.md` already scopes to the client checklist as "not meaningfully
 * testable headless".
 *
 * <p>The meeting point is set directly on the villager's brain (this suite's own convention,
 * already used for {@code JOB_SITE} in {@code ShoppingTripGameTest}), not produced by a real bell:
 * the memory is a bare {@code GlobalPos}, and nothing here needs an actual bell block to exist.
 *
 * <p>A shop 100+ blocks from the test's own small structure sits well outside the chunks Fabric's
 * game test framework keeps loaded around it, so each such shop is placed directly in the level at
 * a real absolute position, through {@code ServerLevel} itself rather than
 * {@code helper.setBlock}/{@code helper.getBlockEntity} — those two take a position relative to the
 * test's own structure and convert it via {@link GameTestHelper#absolutePos}, and
 * {@link GameTestHelper#relativePos} turned out <em>not</em> to be that conversion's inverse (it
 * applies an extra 180-degree rotation around the test origin even for an unrotated test, found by
 * this ticket's own `just check` run placing shops at the wrong position and one game test failing
 * with "missing block entity" at a negated offset) — under a chunk-forced ticket
 * (`ServerLevel#setChunkForced`) taken out before placing it and released again once the test is
 * done, so the chunk survives the tick between placing the shop and searching for it without a
 * player nearby to keep it loaded, and nothing is left forced behind for the rest of the suite.
 *
 * <p>Each test below uses its own rare, mutually distinct item pair, found nowhere else in this
 * suite (`ShoppingTripGameTest`'s own diamond/netherite-ingot precedent, `VC-6`) — and distinct from
 * each other, not only from the rest of the suite: at the default 128-block radius (`VC-18`, up from
 * 48), a shop deliberately placed 100+ blocks from its own test's structure can land within another
 * game test's own search radius just as easily as within a stranger's, and the two tests below that
 * place a shop far away both search with a wide-open filter (any offer of the villager's matches).
 * Confirmed the hard way: this ticket's first `just check` run had both of those tests share one
 * shape and each matched the other's shop instead of its own.
 */
public final class VillageWideShopSearchGameTest {
    private static final int FAR_DISTANCE = 100;
    private static final int TOO_FAR_DISTANCE = 200;

    @GameTest
    public void aShopAtTheMeetingPointsRangeIsFoundWhenTheVillagerStandsThere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item costItem = Items.NETHER_STAR;
        Item priceItem = Items.BEACON;
        BlockPos meetingPointRelative = new BlockPos(1, 2, 1);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, meetingPointRelative);
        villager.getOffers().add(offerFor(costItem, priceItem));

        helper.runAfterDelay(2, () -> {
            BlockPos meetingPoint = helper.absolutePos(meetingPointRelative);
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), meetingPoint));

            BlockPos clothPos = meetingPoint.north(FAR_DISTANCE);
            forceLoad(level, clothPos, true);
            placeShop(level, clothPos);

            helper.runAfterDelay(1, () -> {
                configureCloth(level, clothPos, costItem, priceItem);

                CustomerHooks.Search result = CustomerHooks.search(level, villager);
                helper.assertTrue(
                    result.shop().isPresent() && result.shop().get().pos().equals(clothPos),
                    "a shop " + FAR_DISTANCE + " blocks from the meeting point is found when the villager stands there: " + result.reason()
                );
                helper.assertTrue(result.origin().equals(meetingPoint), "the search origin was the meeting point: " + result.origin());
                helper.assertTrue(
                    result.radius() == CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, "the search used the default radius: " + result.radius()
                );

                forceLoad(level, clothPos, false);
                helper.succeed();
            });
        });
    }

    @GameTest
    public void aShopFarFromTheMeetingPointButNearTheVillagerIsNotFound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item costItem = Items.TOTEM_OF_UNDYING;
        Item priceItem = Items.ENCHANTED_GOLDEN_APPLE;
        BlockPos villagerSpawnRelative = new BlockPos(1, 2, 1);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, villagerSpawnRelative);
        villager.getOffers().add(offerFor(costItem, priceItem));

        helper.runAfterDelay(2, () -> {
            BlockPos villagerPos = villager.blockPosition();
            // The meeting point sits FAR_DISTANCE north of the villager; the shop sits FAR_DISTANCE
            // south of the villager, on the opposite side, so it is FAR_DISTANCE from the villager
            // but TOO_FAR_DISTANCE (twice that) from the meeting point.
            BlockPos meetingPoint = villagerPos.north(FAR_DISTANCE);
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), meetingPoint));

            BlockPos clothPos = villagerPos.south(FAR_DISTANCE);
            helper.assertTrue(
                clothPos.distManhattan(meetingPoint) >= TOO_FAR_DISTANCE, "the shop is genuinely farther than the radius from the meeting point"
            );
            forceLoad(level, clothPos, true);
            placeShop(level, clothPos);

            helper.runAfterDelay(1, () -> {
                configureCloth(level, clothPos, costItem, priceItem);

                CustomerHooks.Search result = CustomerHooks.search(level, villager);
                helper.assertTrue(
                    result.shop().isEmpty(),
                    "a shop " + TOO_FAR_DISTANCE + " blocks from the meeting point is not found even though it is only " + FAR_DISTANCE
                        + " from the villager: " + result.reason()
                );
                helper.assertTrue(
                    result.origin().equals(meetingPoint), "the search origin was the meeting point, not the villager: " + result.origin()
                );

                // Sanity check: the same shop *is* reachable from the villager's own position at the
                // production radius, proving it was excluded by the origin choice, not by some other
                // reason (e.g. a placement mistake).
                Optional<Shop> fromVillager = ShopSearch.matching(
                    level, villagerPos, CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, candidate -> CustomerHooks.matchingOffer(villager, candidate).isPresent()
                );
                helper.assertTrue(
                    fromVillager.isPresent() && fromVillager.get().pos().equals(clothPos),
                    "the same shop is found when searching from the villager's own position instead"
                );

                forceLoad(level, clothPos, false);
                helper.succeed();
            });
        });
    }

    @GameTest
    public void aVillagerWithNoMeetingPointSearchesFromItsOwnPosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item costItem = Items.HEART_OF_THE_SEA;
        Item priceItem = Items.CONDUIT;
        BlockPos clothRelative = new BlockPos(1, 1, 5);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.getOffers().add(offerFor(costItem, priceItem));

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        BlockPos tickerRelative = clothRelative.east(1);
        BlockPos keeperRelative = tickerRelative.south();
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).isEmpty(), "a fresh villager remembers no meeting point"
            );

            BlockPos clothPos = helper.absolutePos(clothRelative);
            configureCloth(level, clothPos, costItem, priceItem);

            CustomerHooks.Search result = CustomerHooks.search(level, villager);
            helper.assertTrue(
                result.shop().isPresent() && result.shop().get().pos().equals(clothPos),
                "the nearby shop is found with no meeting point set: " + result.reason()
            );
            helper.assertTrue(
                result.origin().equals(villager.blockPosition()), "the search origin was the villager's own position: " + result.origin()
            );
            helper.succeed();
        });
    }

    /** A table cloth, stock ticker and keeper at {@code clothPos} and its two neighbours, placed directly in the level (not the test
     * structure) via raw {@link ServerLevel#setBlock} calls at true absolute positions (see this class's own Javadoc). */
    private static void placeShop(ServerLevel level, BlockPos clothPos) {
        BlockPos tickerPos = clothPos.east(1);
        BlockPos keeperPos = tickerPos.south();
        level.setBlock(clothPos, AllBlocks.ANDESITE_TABLE_CLOTH.defaultBlockState(), 3);
        level.setBlock(tickerPos, AllBlocks.STOCK_TICKER.defaultBlockState(), 3);
        level.setBlock(
            keeperPos, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING), 3
        );
    }

    /** Prices and links the cloth at {@code clothPos} — one {@code priceItem} for {@code costItem}, matching {@link #offerFor}'s own shape. */
    private static void configureCloth(ServerLevel level, BlockPos clothPos, Item costItem, Item priceItem) {
        BlockPos tickerPos = clothPos.east(1);
        TableClothBlockEntity cloth = (TableClothBlockEntity) level.getBlockEntity(clothPos);
        cloth.priceTag.setFilter(new ItemStack(priceItem));
        cloth.priceTag.count = 1;
        cloth.requestData = new AutoRequestData(
            PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(costItem), 1))), "", tickerPos.subtract(clothPos), "", true
        );
    }

    /** Forces or releases a chunk-loading ticket over {@code pos}'s chunk and its 8 neighbours (`VC-18`'s own Approach: "place POIs directly
     * in the level outside the structure and clean up"), enough margin for the cloth/ticker/keeper cluster even if {@code pos} sits near a
     * chunk boundary. */
    private static void forceLoad(ServerLevel level, BlockPos pos, boolean forced) {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(chunkPos.x() + dx, chunkPos.z() + dz, forced);
            }
        }
    }

    private static MerchantOffer offerFor(Item costItem, Item priceItem) {
        return new MerchantOffer(new ItemCost(costItem, 1), new ItemStack(priceItem, 1), 2, 10, 0.0f);
    }
}
