package villager_customers.keeper;

import com.zurrtum.create.content.contraptions.actors.seat.SeatBlock;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Searches for eligible Create seats near a stock ticker, nearest to the search origin first
 * (`docs/spec/domains/keeper.md` §3 "Seat eligibility", `KEEPER-REQ-005`..`007`; mirrors
 * {@code villager_customers.shop.ShopSearch}'s own "nearest first" convention, `SHOP-DEC-001`).
 *
 * <p><b>Geometry</b> (`04-architecture.md` `ARCH-DEC-006`; the vault research pass's §G.1, confirmed
 * again directly against the Create Fly jar by {@code javap -p -c StockTickerBlockEntity}): a seat
 * counts as adjacent to a ticker exactly where {@code StockTickerBlockEntity.isKeeperPresent()}
 * itself looks — two below-offsets ({@code 0} and {@code 1}) crossed with the four horizontal
 * directions, eight candidate positions per ticker, the same geometry that method scans for an
 * occupied {@code SeatEntity} (or, at offset {@code 0} only, a {@code HEATER} block entity —
 * irrelevant here, since this class only ever places a nitwit on an actual {@code SeatBlock}).
 *
 * <p><b>Why a chunk scan, not {@code PoiManager}:</b> Create Fly registers no
 * {@code PointOfInterestType} over its seat blocks or its stock ticker (confirmed: no
 * {@code point_of_interest_type} data in the Create Fly jar, and {@code AllBlocks}/{@code
 * AllBlockEntityTypes} carry no such registration) — seats are not points of interest the way table
 * cloths are ({@code villager_customers.shop.ShopPoi}), so there is no POI index to query. Anchoring
 * on stock tickers instead (this ticket's own Build step 1): for every chunk column whose bounds
 * intersect the {@code (origin, radius)} square — the same square {@code PoiManager.getInRange}
 * itself walks per {@code ShopSearch.near}'s own Javadoc, {@code (2*(radius/16+1)+1)}²  columns
 * ({@code 19x19 = 361} for the default 128-block radius) — this calls {@code
 * ServerChunkCache.getChunkNow(x, z)}, which returns {@code null} for a column that is not currently
 * loaded rather than force-loading it, and reads the loaded {@code LevelChunk}'s own {@code
 * getBlockEntities()} map for every {@code StockTickerBlockEntity} in it. That is one map read per
 * loaded chunk column in range, then up to eight block-state and block-entity reads per keeperless
 * ticker found (the geometry above). Cheap at {@code KEEPER-REQ-004}'s once-per-{@code
 * keeper_seek_cooldown_ticks} frequency (24000 ticks — one vanilla day — by default), the same cost
 * argument {@code docs/spec/decisions/DEC-010-village-wide-shop-search.md} already accepts for
 * {@code CUSTOMER}'s own per-restock search; unlike that search, this one also touches every
 * <em>unoccupied</em> stock ticker in range even when it never yields a seat, since eligibility can
 * only be known after the ticker itself is read.
 */
public final class KeeperSeatSearch {
    private static final int[] BELOW_OFFSETS = {0, 1};
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private KeeperSeatSearch() {
    }

    /**
     * Every eligible, unclaimed seat within {@code radius} of {@code origin} (`KEEPER-REQ-005`,
     * `006`), nearest to {@code origin} first (`KEEPER-REQ-007`). {@code gameTime} is used only to
     * tell an expired claim from a live one ({@code SeatClaims}); this method itself claims nothing.
     */
    public static List<GlobalPos> near(ServerLevel level, BlockPos origin, int radius, long gameTime) {
        List<GlobalPos> found = new ArrayList<>();
        double radiusSq = (double) radius * radius;

        int minChunkX = Math.floorDiv(origin.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(origin.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(origin.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(origin.getZ() + radius, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue; // not currently loaded; never force-loaded for this search
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof StockTickerBlockEntity ticker)) {
                        continue;
                    }
                    BlockPos tickerPos = entry.getKey();
                    if (tickerPos.distSqr(origin) > radiusSq || ticker.isKeeperPresent()) {
                        continue;
                    }
                    for (BlockPos seatPos : seatCandidates(tickerPos)) {
                        GlobalPos globalSeatPos = GlobalPos.of(level.dimension(), seatPos);
                        if (isEligibleSeatBlock(level, seatPos) && !SeatClaims.isClaimed(globalSeatPos, gameTime)) {
                            found.add(globalSeatPos);
                        }
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.pos().distSqr(origin)));
        return found;
    }

    /**
     * A live recheck of one seat's own eligibility (block, occupancy, and its ticker's keeper
     * presence), the same "recomputed fresh every call, nothing cached" convention {@code
     * villager_customers.shop.Shop#at} already uses (`SHOP-DEC-002`-style). Used by {@code
     * KeeperSeekBehavior#canStillUse} to decide whether a claimed seat is still worth walking to.
     */
    public static boolean isEligibleSeat(ServerLevel level, BlockPos seatPos) {
        if (!isEligibleSeatBlock(level, seatPos)) {
            return false;
        }
        return tickerFor(level, seatPos).map(ticker -> !ticker.isKeeperPresent()).orElse(false);
    }

    /** {@code seatPos} is a Create seat block and nothing is currently sitting in it (occupancy only; the ticker's own keeper state is a separate check). */
    private static boolean isEligibleSeatBlock(ServerLevel level, BlockPos seatPos) {
        return level.getBlockState(seatPos).getBlock() instanceof SeatBlock && !SeatBlock.isSeatOccupied(level, seatPos);
    }

    /**
     * The stock ticker {@code seatPos} is adjacent to, per the same geometry {@link #seatCandidates}
     * builds forward, inverted: for each of the eight {@code (offset, direction)} combinations, the
     * ticker that would have produced {@code seatPos} sits at {@code seatPos.above(offset)
     * .relative(direction.getOpposite())} (below/above and a horizontal step commute, so this is a
     * true inverse). At most one real ticker is expected to match in normal play.
     */
    private static Optional<StockTickerBlockEntity> tickerFor(ServerLevel level, BlockPos seatPos) {
        for (int offset : BELOW_OFFSETS) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos candidateTicker = seatPos.above(offset).relative(direction.getOpposite());
                if (level.getBlockEntity(candidateTicker) instanceof StockTickerBlockEntity ticker) {
                    return Optional.of(ticker);
                }
            }
        }
        return Optional.empty();
    }

    /** The eight seat positions {@code isKeeperPresent()} itself scans around {@code tickerPos} (this class's own Javadoc). */
    private static List<BlockPos> seatCandidates(BlockPos tickerPos) {
        List<BlockPos> candidates = new ArrayList<>(BELOW_OFFSETS.length * HORIZONTAL_DIRECTIONS.length);
        for (int offset : BELOW_OFFSETS) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                candidates.add(tickerPos.below(offset).relative(direction));
            }
        }
        return candidates;
    }
}
