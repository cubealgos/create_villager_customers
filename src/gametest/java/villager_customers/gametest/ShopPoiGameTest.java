package villager_customers.gametest;

import com.zurrtum.create.AllBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import villager_customers.shop.ShopPoi;

/**
 * A table cloth's own block-entity load/unload keeps the village point-of-interest index in step
 * with {@code villager_customers:table_cloth_shop} (VC-2, `SHOP-REQ-001`).
 */
public final class ShopPoiGameTest {
    @GameTest
    public void tableClothAppearsAndDisappearsAsPoi(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, AllBlocks.ANDESITE_TABLE_CLOTH);

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(relative);
            helper.assertTrue(
                level.getPoiManager().exists(pos, type -> type.is(ShopPoi.KEY)),
                "a placed table cloth is registered as a shop point of interest"
            );

            helper.destroyBlock(relative);
            helper.runAfterDelay(3, () -> {
                helper.assertFalse(
                    level.getPoiManager().exists(pos, type -> type.is(ShopPoi.KEY)),
                    "breaking the table cloth removes the shop point of interest"
                );
                helper.succeed();
            });
        });
    }
}
