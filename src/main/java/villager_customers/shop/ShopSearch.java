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
 * (`docs/spec/domains/shop.md` `SHOP-REQ-004`, `SHOP-REQ-006`; `SHOP-DEC-001`).
 */
public final class ShopSearch {

    /**
     * The search radius: vanilla's own job-site point-of-interest scan range, so this mod adds no
     * distance of its own (`SHOP-REQ-004`). Verified as 48 via {@code javap -v -p} against the
     * constant pool of {@code minecraft-merged-deobf-26.2.jar}:
     * {@code net.minecraft.world.entity.ai.behavior.AcquirePoi.SCAN_RANGE}, the range vanilla's
     * own {@code GoToPotentialJobSite}/potential-job-site search passes to the village
     * point-of-interest index.
     */
    public static final int VILLAGE_REACH = 48;

    private ShopSearch() {
    }

    /**
     * Every shop within {@code radius} of {@code origin}, nearest first (`SHOP-DEC-001`).
     *
     * <p>Many table cloths commonly share one stock ticker, so {@code isKeeperPresent()} is
     * resolved once per distinct ticker position rather than once per cloth — cloths whose ticker
     * fails that check never reach {@link Shop#at}, which still performs its own full, live check
     * per cloth (`SHOP-DEC-002`) on the survivors.
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

    /** Every shop within {@link #VILLAGE_REACH} of {@code origin}, nearest first. */
    public static List<Shop> near(ServerLevel level, BlockPos origin) {
        return near(level, origin, VILLAGE_REACH);
    }

    /** The nearest shop within {@code radius} of {@code origin} that satisfies {@code filter}. */
    public static Optional<Shop> matching(ServerLevel level, BlockPos origin, int radius, Predicate<Shop> filter) {
        return near(level, origin, radius).stream().filter(filter).findFirst();
    }
}
