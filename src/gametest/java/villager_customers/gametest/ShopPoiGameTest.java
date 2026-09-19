package villager_customers.gametest;

import com.zurrtum.create.AllBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import villager_customers.shop.ShopPoi;

/**
 * Vanilla's own point-of-interest discovery — the block place/break hook and the chunk-load
 * consistency scan, both reached through {@code PoiTypes.forState} — keeps the village
 * point-of-interest index in step with {@code villager_customers:table_cloth_shop} with no sync of
 * this mod's own, since {@link ShopPoi} registers through Fabric's {@code PoiHelper} (VC-2,
 * `SHOP-REQ-001`). Drives only {@code helper.setBlock}/{@code destroyBlock} — no block-entity
 * poking — to prove that pipeline, not a manual one.
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
