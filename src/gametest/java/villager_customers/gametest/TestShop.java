package villager_customers.gametest;

import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import villager_customers.transaction.ShopAccess;

/**
 * A minimal {@link ShopAccess} built directly in the game test, standing in for VC-2's own
 * {@code villager_customers.shop.Shop} (see {@link ShopAccess}'s own Javadoc).
 */
record TestShop(List<ItemStack> goods, ItemStack price, StockTickerBlockEntity ticker, BlockPos pos) implements ShopAccess {
}
