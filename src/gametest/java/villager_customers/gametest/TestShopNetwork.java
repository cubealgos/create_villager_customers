package villager_customers.gametest;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.packager.PackagerBlockEntity;
import com.zurrtum.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.zurrtum.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * A small, real Create Fly logistics network for the transaction game tests: a chest, a packager
 * targeting it, a stock link bound to the packager, and a stock ticker on the same frequency
 * (`docs/spec/domains/transaction.md`). Not a {@code @GameTest} class itself — nothing here needs
 * registering.
 */
final class TestShopNetwork {
    final BlockPos chestPos;
    final BlockPos packagerPos;
    final ChestBlockEntity chest;
    final PackagerBlockEntity packager;
    final StockTickerBlockEntity ticker;

    private TestShopNetwork(BlockPos chestPos, BlockPos packagerPos, ChestBlockEntity chest, PackagerBlockEntity packager, StockTickerBlockEntity ticker) {
        this.chestPos = chestPos;
        this.packagerPos = packagerPos;
        this.chest = chest;
        this.packager = packager;
        this.ticker = ticker;
    }

    /** Builds the network with {@code wheatCount} wheat already in the chest (0 for an empty chest). */
    static TestShopNetwork build(GameTestHelper helper, int wheatCount) {
        BlockPos chestPos = new BlockPos(1, 1, 2);
        BlockPos packagerPos = new BlockPos(1, 1, 3);
        BlockPos stockLinkPos = packagerPos.above();
        BlockPos tickerPos = new BlockPos(1, 1, 5);

        helper.setBlock(chestPos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        setWheat(chest, wheatCount);

        // FACING = SOUTH: the packager's target inventory (opposite of facing, TransactionExecutor's
        // research pass) is to the NORTH, at chestPos.
        helper.setBlock(packagerPos, AllBlocks.PACKAGER.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        PackagerBlockEntity packager = helper.getBlockEntity(packagerPos, PackagerBlockEntity.class);
        packager.targetInventory.findNewCapability(); // resolve synchronously; no tick delay needed

        // A floor-attached stock link, sitting directly on top of the packager, binds to it
        // (PackagerLinkBlockEntity.getPackager() walks straight down for a FLOOR attach face).
        helper.setBlock(stockLinkPos, AllBlocks.STOCK_LINK.defaultBlockState()
            .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        PackagerLinkBlockEntity link = helper.getBlockEntity(stockLinkPos, PackagerLinkBlockEntity.class);

        helper.setBlock(tickerPos, AllBlocks.STOCK_TICKER.defaultBlockState());
        StockTickerBlockEntity ticker = helper.getBlockEntity(tickerPos, StockTickerBlockEntity.class);

        UUID freqId = UUID.randomUUID();
        link.behaviour.freqId = freqId;
        ticker.behaviour.freqId = freqId;
        LogisticallyLinkedBehaviour.keepAlive(link.behaviour);
        LogisticallyLinkedBehaviour.keepAlive(ticker.behaviour);

        return new TestShopNetwork(chestPos, packagerPos, chest, packager, ticker);
    }

    /** Replaces the chest's single wheat stack (0 empties it). */
    void setWheat(int wheatCount) {
        setWheat(chest, wheatCount);
    }

    private static void setWheat(ChestBlockEntity chest, int wheatCount) {
        chest.setItem(0, wheatCount > 0 ? new ItemStack(Items.WHEAT, wheatCount) : ItemStack.EMPTY);
    }

    /** Fills every slot of the payment box with an unrelated full stack, leaving it no room at all. */
    void fillPaymentBoxWithJunk() {
        var box = ticker.receivedPayments;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            box.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }
}
