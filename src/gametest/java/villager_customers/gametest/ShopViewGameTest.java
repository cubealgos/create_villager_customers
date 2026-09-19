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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.shop.Shop;
import villager_customers.transaction.ShopAccess;
import villager_customers.transaction.TransactionExecutor;

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

    /**
     * {@link Shop#at} returned as a {@link ShopAccess} for {@code VC-3}'s
     * {@code TransactionExecutor}, executed against a real chest-and-packager network
     * ({@link TestShopNetwork}, reused from VC-3's own game tests): a matched offer draws the
     * table cloth's requested wheat and completes one unit, proving {@code Shop} is a drop-in
     * {@code ShopAccess} rather than merely typing as one.
     */
    @GameTest(maxTicks = 200)
    public void aRealShopExecutesATransactionAsShopAccess(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 20); // enough stock for exactly one unit
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos clothRelative = new BlockPos(1, 1, 7);
        BlockPos keeperRelative = tickerRelative.east(1);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));

        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);

        helper.runAfterDelay(2, () -> {
            BlockPos clothPos = helper.absolutePos(clothRelative);
            TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
            cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
            cloth.priceTag.count = 1;
            cloth.requestData = new AutoRequestData(
                PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.WHEAT), 20))),
                "",
                network.ticker.getBlockPos().subtract(clothPos),
                "",
                true
            );

            ShopAccess shop = Shop.at(level, clothPos).orElseThrow();
            TransactionExecutor.Result result = TransactionExecutor.execute(level, villager, offer, shop);
            helper.assertTrue(
                result.unitsCompleted() == 1,
                "one unit completed against a real network through Shop as ShopAccess: " + result
            );
            helper.succeed();
        });
    }
}
