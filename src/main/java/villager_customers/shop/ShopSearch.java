package villager_customers.shop;

import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Searches the village point-of-interest index for shop candidates, nearest first
 * (`docs/spec/domains/shop.md` `SHOP-REQ-004`, `SHOP-REQ-006`; `SHOP-DEC-001`). The search radius
 * itself is the caller's choice — {@code villager_customers.customer.CustomerHooks} resolves it
 * from the mod's config (`docs/spec/decisions/DEC-010-village-wide-shop-search.md`,
 * `CUSTOMER-REQ-003`); this class adds no default of its own, so game tests can pass a small
 * radius that stays clear of neighbouring test structures (`ShopSearchGameTest`'s own Javadoc).
 */
public final class ShopSearch {

    private ShopSearch() {
    }

    /**
     * Every shop within {@code radius} of {@code origin}, nearest first (`SHOP-DEC-001`).
     *
     * <p>Many table cloths commonly share one stock ticker, so {@code isKeeperPresent()} is
     * resolved once per distinct ticker position rather than once per cloth — cloths whose ticker
     * fails that check never reach {@link Shop#at}, which still performs its own full, live check
     * per cloth (`SHOP-DEC-002`) on the survivors.
     *
     * <p>{@code radius} is passed straight to {@link PoiManager#getInRange}. Disassembled via
     * {@code javap -p -c} against {@code minecraft-merged-deobf-26.2.jar} (`VC-18`): {@code
     * getInRange} calls {@code getInSquare}, which walks every chunk column in a
     * {@code (2 * (radius / 16 + 1) + 1)}-wide square of columns around {@code origin} — for
     * {@code radius = 128} that is 19×19 = 361 columns, against 9×9 = 81 for the old 48-block reach,
     * so this ticket's default costs roughly 4.5× as many columns per query — and within each
     * column iterates <em>every</em> vertical point-of-interest section the level has, from
     * {@code LevelHeightAccessor.getMinSectionY()} to {@code getMaxSectionY()}: the section walk
     * itself is not pruned by {@code radius} vertically at all, only horizontally. {@code
     * getInRange} then filters that candidate stream by a true 3D {@code BlockPos.distSqr}, so the
     * <em>result</em> is a sphere of radius {@code radius} in every direction — a point-of-interest
     * far above or below {@code origin} is excluded exactly as one far to the side would be, even
     * though the section scan that produced the candidates touched sections at every height. One
     * query per successful restock roll per villager is cheap at that frequency regardless
     * (`DEC-010`'s own cost note).
     */
    public static List<Shop> near(ServerLevel level, BlockPos origin, int radius) {
        PoiManager poiManager = level.getPoiManager();
        List<BlockPos> clothPositions = poiManager
            .getInRange(type -> type.is(ShopPoi.KEY), origin, radius, PoiManager.Occupancy.ANY)
            .map(PoiRecord::getPos)
            .collect(Collectors.toList());

        Map<BlockPos, List<BlockPos>> clothsByTicker = new LinkedHashMap<>();
        for (BlockPos clothPos : clothPositions) {
            if (level.getBlockEntity(clothPos) instanceof TableClothBlockEntity cloth) {
                BlockPos tickerPos = clothPos.offset(cloth.requestData.targetOffset());
                clothsByTicker.computeIfAbsent(tickerPos, key -> new ArrayList<>()).add(clothPos);
            }
        }

        Set<BlockPos> keeperPresentTickers = clothsByTicker.keySet().stream()
            .filter(tickerPos -> level.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity ticker && ticker.isKeeperPresent())
            .collect(Collectors.toSet());

        return clothsByTicker.entrySet().stream()
            .filter(entry -> keeperPresentTickers.contains(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .flatMap(pos -> Shop.at(level, pos).stream())
            .sorted(Comparator.comparingDouble(shop -> shop.pos().distSqr(origin)))
            .collect(Collectors.toUnmodifiableList());
    }

    /** The nearest shop within {@code radius} of {@code origin} that satisfies {@code filter}. */
    public static Optional<Shop> matching(ServerLevel level, BlockPos origin, int radius, Predicate<Shop> filter) {
        return near(level, origin, radius).stream().filter(filter).findFirst();
    }
}
