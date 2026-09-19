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

import java.util.List;
import java.util.Optional;

/**
 * A minimal real shop — a table cloth with a price and an encoded request, linked to a stock
 * ticker with a keeper present — read through {@link Shop#at} (VC-2, `SHOP-REQ-002`,
 * `SHOP-REQ-003`).
 *
 * <p>{@code TableClothBlockEntity.requestData} and {@code priceTag} are public fields on the real
 * block entity (confirmed via {@code javap}), so this test drives them directly rather than
 * through the shopping-list item a real player carries; that pledge-then-checkout round trip is
 * Create Fly's own player checkout and is out of this mod's scope (`ARCH-DEC-004`).
 *
 * <p>A keeper is an {@code AllBlocks.BLAZE_BURNER} with a non-{@code NONE} {@code HEAT_LEVEL},
 * placed beside the stock ticker at the ticker's own height.
 * {@code StockTickerBlockEntity.isKeeperPresent()} (disassembled via {@code javap -p -c}) accepts
 * any block entity of Create Fly's {@code AllBlockEntityTypes.HEATER} type at that position; the
 * heat level only matters because {@code BlazeBurnerBlock.newBlockEntity} (also disassembled)
 * returns {@code null} — no block entity at all — for the default, unlit {@code NONE} state. No
 * logistics-manager entity had to be spawned headless for this test.
 */
public final class ShopViewGameTest {
    @GameTest
    public void aWellFormedShopIsFoundAndLosesCandidacyCorrectly(GameTestHelper helper) {
        BlockPos clothRelative = new BlockPos(1, 1, 1);
        BlockPos tickerRelative = clothRelative.east(2);
        BlockPos keeperRelative = tickerRelative.south();

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(tickerRelative, AllBlocks.STOCK_TICKER);
        helper.setBlock(keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));

        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos clothPos = helper.absolutePos(clothRelative);
            BlockPos tickerPos = helper.absolutePos(tickerRelative);

            TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
            cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
            cloth.priceTag.count = 3;
            cloth.requestData = new AutoRequestData(
                PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.BREAD), 4))),
                "",
                tickerPos.subtract(clothPos),
                "",
                true
            );

            Optional<Shop> shop = Shop.at(level, clothPos);
            helper.assertTrue(shop.isPresent(), "a table cloth with a price, a request and a keeper-present ticker is a shop");

            ItemStack price = shop.get().price();
            helper.assertTrue(price.getItem() == Items.EMERALD && price.getCount() == 3, "the shop's price matches the table cloth's price tag");

            List<ItemStack> goods = shop.get().goods();
            helper.assertTrue(
                goods.size() == 1 && goods.get(0).getItem() == Items.BREAD && goods.get(0).getCount() == 4,
                "the shop's goods match the table cloth's requested stacks"
            );

            cloth.priceTag.setFilter(ItemStack.EMPTY);
            helper.assertTrue(Shop.at(level, clothPos).isEmpty(), "clearing the price makes the table cloth not a shop");

            cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
            cloth.priceTag.count = 3;
            helper.assertTrue(Shop.at(level, clothPos).isPresent(), "restoring the price makes the table cloth a shop again");

            helper.destroyBlock(tickerRelative);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(Shop.at(level, clothPos).isEmpty(), "removing the linked stock ticker makes the table cloth not a shop");
                helper.succeed();
            });
        });
    }
}
