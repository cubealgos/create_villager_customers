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

/** Two shops, one nearer: {@link ShopSearch#near} returns the nearer one first (VC-2, `SHOP-DEC-001`). */
public final class ShopSearchGameTest {
    @GameTest
    public void nearestShopComesFirst(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos nearCloth = new BlockPos(1, 1, 1);
        BlockPos farCloth = new BlockPos(6, 1, 6);

        placeShop(helper, nearCloth, nearCloth.east(2), nearCloth.east(2).south());
        placeShop(helper, farCloth, farCloth.west(2), farCloth.west(2).north());

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos originPos = helper.absolutePos(origin);
            BlockPos nearPos = helper.absolutePos(nearCloth);
            BlockPos farPos = helper.absolutePos(farCloth);

            List<Shop> found = ShopSearch.near(level, originPos, ShopSearch.VILLAGE_REACH);
            helper.assertTrue(found.size() == 2, "both shops are found");
            helper.assertTrue(found.get(0).pos().equals(nearPos), "the nearer shop comes first");
            helper.assertTrue(found.get(1).pos().equals(farPos), "the farther shop comes second");
            helper.succeed();
        });
    }

    private static void placeShop(GameTestHelper helper, BlockPos clothRelative, BlockPos tickerRelative, BlockPos keeperRelative) {
        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
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
