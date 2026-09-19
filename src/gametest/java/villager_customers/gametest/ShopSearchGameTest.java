package villager_customers.gametest;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import villager_customers.shop.Shop;
import villager_customers.shop.ShopSearch;

import java.util.List;

/**
 * Two shops, one nearer: {@link ShopSearch#near} returns the nearer one first (VC-2,
 * `SHOP-DEC-001`).
 *
 * <p>Every position here stays within a few blocks of the test's own origin (never the full
 * {@link ShopSearch#VILLAGE_REACH}), and every search below passes a small explicit radius rather
 * than that constant: Fabric's game test batches place structures roughly a dozen blocks apart
 * (`net.minecraft.gametest.framework.StructureGridSpawner`, structure size plus a small margin), so
 * a 48-block search from a test this close to its neighbours picks up their point-of-interest
 * records too — confirmed by reproducing it once with a debug dump of the search's internal
 * grouping. {@code VILLAGE_REACH}'s value itself is verified directly against
 * {@code AcquirePoi.SCAN_RANGE} (see {@link ShopSearch}'s own Javadoc), not by this game test.
 */
public final class ShopSearchGameTest {
    private static final int TEST_RADIUS = 8;

    @GameTest
    public void nearestShopComesFirst(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos nearCloth = new BlockPos(1, 1, 1);
        BlockPos farCloth = new BlockPos(1, 1, 4);

        placeShop(helper, nearCloth, nearCloth.east(1), nearCloth.east(1).south());
        placeShop(helper, farCloth, farCloth.east(1), farCloth.east(1).north());

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos originPos = helper.absolutePos(origin);
            BlockPos nearPos = helper.absolutePos(nearCloth);
            BlockPos farPos = helper.absolutePos(farCloth);

            List<Shop> found = ShopSearch.near(level, originPos, TEST_RADIUS);
            helper.assertTrue(found.size() == 2, "both shops are found");
            helper.assertTrue(found.get(0).pos().equals(nearPos), "the nearer shop comes first");
            helper.assertTrue(found.get(1).pos().equals(farPos), "the farther shop comes second");
            helper.succeed();
        });
    }

    /**
     * VC-6 sweep gap: `SHOP-REQ-004`'s "no distance beyond vanilla's own added" and `SHOP-FAIL-001`
     * ("table cloth built outside any village's reach ... invisible to search; not a bug") were only
     * exercised by tests that stay well inside whatever radius they pass — nothing proved a shop
     * genuinely beyond the search radius is excluded rather than merely never having been tried. A
     * shop at distance 6 with radius 3 is outside; the same shop is inside once the radius covers it.
     */
    @GameTest
    public void aShopBeyondTheSearchRadiusIsInvisibleToTheSearch(GameTestHelper helper) {
        int smallRadius = 3;
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos nearCloth = new BlockPos(1, 1, 1);
        BlockPos farCloth = new BlockPos(1, 1, 6);

        placeShop(helper, nearCloth, nearCloth.east(1), nearCloth.east(1).south());
        placeShop(helper, farCloth, farCloth.east(1), farCloth.east(1).north());

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos originPos = helper.absolutePos(origin);
            BlockPos nearPos = helper.absolutePos(nearCloth);

            List<Shop> foundNarrow = ShopSearch.near(level, originPos, smallRadius);
            helper.assertTrue(foundNarrow.size() == 1, "only the near shop is within the small radius: " + foundNarrow.size());
            helper.assertTrue(foundNarrow.get(0).pos().equals(nearPos), "the shop found within the small radius is the near one");

            List<Shop> foundWide = ShopSearch.near(level, originPos, TEST_RADIUS);
            helper.assertTrue(foundWide.size() == 2, "both shops are within the wider radius: " + foundWide.size());
            helper.succeed();
        });
    }

    /**
     * Two cloths sharing one stock ticker (`ShopSearch#near`'s once-per-ticker keeper check,
     * VC-2): both are absent while the shared ticker has no keeper, and both appear once one is
     * placed.
     */
    @GameTest
    public void bothClothsOnAKeeperlessSharedTickerWaitTogether(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos tickerRelative = new BlockPos(2, 1, 1);
        BlockPos keeperRelative = tickerRelative.south();
        BlockPos clothARelative = new BlockPos(1, 1, 1);
        BlockPos clothBRelative = new BlockPos(1, 1, 3);

        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        placeCloth(helper, clothARelative, tickerRelative);
        placeCloth(helper, clothBRelative, tickerRelative);

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos originPos = helper.absolutePos(origin);

            helper.assertTrue(
                ShopSearch.near(level, originPos, TEST_RADIUS).isEmpty(),
                "both cloths on a keeper-less shared ticker are absent"
            );

            helper.setBlock(keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(
                    ShopSearch.near(level, originPos, TEST_RADIUS).size() == 2,
                    "both cloths are present once the shared ticker has a keeper"
                );
                helper.succeed();
            });
        });
    }

    private static void placeShop(GameTestHelper helper, BlockPos clothRelative, BlockPos tickerRelative, BlockPos keeperRelative) {
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        placeCloth(helper, clothRelative, tickerRelative);
    }

    private static void placeCloth(GameTestHelper helper, BlockPos clothRelative, BlockPos tickerRelative) {
        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.runAfterDelay(1, () -> {
            TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
            cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
            cloth.priceTag.count = 1;
            cloth.requestData = new AutoRequestData(
                PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.BREAD), 1))),
                "",
                tickerRelative.subtract(clothRelative),
                "",
                true
            );
        });
    }
}
