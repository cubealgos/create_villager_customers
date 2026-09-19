package villager_customers.shop;

import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import villager_customers.transaction.ShopAccess;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A live view onto a Create Fly table cloth that currently counts as a shop
 * (`docs/spec/domains/shop.md` `SHOP-REQ-002`, `SHOP-REQ-003`). Never a stored or cached object:
 * {@link #at} recomputes shophood from the world on every call, and nothing here survives past the
 * call that produced it (`SHOP-DEC-002`), since a shop's candidacy can end at any time
 * (`UC-007`). Implements {@link ShopAccess}, the minimal view `VC-3`'s
 * {@code villager_customers.transaction.TransactionExecutor} reads a shop through.
 */
public final class Shop implements ShopAccess {
    private final BlockPos pos;
    private final TableClothBlockEntity cloth;
    private final StockTickerBlockEntity ticker;

    private Shop(BlockPos pos, TableClothBlockEntity cloth, StockTickerBlockEntity ticker) {
        this.pos = pos;
        this.cloth = cloth;
        this.ticker = ticker;
    }

    /**
     * A shop only when the block entity at {@code pos} is a table cloth that {@code isShop()},
     * carries a non-empty price, and resolves a linked stock ticker whose
     * {@code isKeeperPresent()} is true (`SHOP-REQ-002`, `SHOP-REQ-003`) — recomputed fresh every
     * call; nothing about this check is cached across ticks (`SHOP-DEC-002`).
     */
    public static Optional<Shop> at(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TableClothBlockEntity cloth) || !cloth.isShop()) {
            return Optional.empty();
        }
        ItemStack price = cloth.getPaymentItem();
        if (price.isEmpty() || cloth.getPaymentAmount() <= 0) {
            return Optional.empty();
        }
        BlockPos tickerPos = pos.offset(cloth.requestData.targetOffset());
        BlockEntity tickerEntity = level.getBlockEntity(tickerPos);
        if (!(tickerEntity instanceof StockTickerBlockEntity ticker) || !ticker.isKeeperPresent()) {
            return Optional.empty();
        }
        return Optional.of(new Shop(pos, cloth, ticker));
    }

    /** The goods this shop's table cloth requests, one stack per requested item. */
    @Override
    public List<ItemStack> goods() {
        return cloth.requestData.encodedRequest().stacks().stream()
            .map(Shop::asStack)
            .collect(Collectors.toUnmodifiableList());
    }

    /** The price this shop's table cloth asks for one unit of its goods. */
    @Override
    public ItemStack price() {
        return cloth.getPaymentItem().copyWithCount(cloth.getPaymentAmount());
    }

    /** The stock ticker backing this shop's network stock and payment box. */
    @Override
    public StockTickerBlockEntity ticker() {
        return ticker;
    }

    /** This shop's table cloth position. */
    @Override
    public BlockPos pos() {
        return pos;
    }

    private static ItemStack asStack(BigItemStack big) {
        return big.stack.copyWithCount(big.count);
    }
}
