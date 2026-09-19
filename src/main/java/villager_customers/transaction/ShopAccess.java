package villager_customers.transaction;

import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * The minimal, read-only view of a table-cloth shop that {@link TransactionExecutor} needs
 * (`docs/spec/domains/transaction.md`). This ticket (`VC-3`) defines the interface; VC-2's own
 * {@code villager_customers.shop.Shop} implements it once merged — the two branches were built in
 * parallel with neither seeing the other's classes, so this is deliberately the narrowest surface a
 * shop can offer rather than a copy of {@code Shop}'s own API.
 */
public interface ShopAccess {
    /** The table cloth's configured goods: what a matched offer draws from the network stock. */
    List<ItemStack> goods();

    /** The table cloth's configured price: what a matched offer pays into the payment box. */
    ItemStack price();

    /** The linked stock ticker whose network stock and payment box the transaction reads and writes. */
    StockTickerBlockEntity ticker();

    /** The table cloth's own position. */
    BlockPos pos();
}
