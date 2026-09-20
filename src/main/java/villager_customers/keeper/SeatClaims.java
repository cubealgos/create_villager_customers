package villager_customers.keeper;

import net.minecraft.core.GlobalPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The non-persistent per-server claim a seeking nitwit holds on a seat, so two concurrent seeks
 * never both walk to the same one (`docs/spec/domains/keeper.md` `KEEPER-REQ-007`; this ticket's
 * own Approach, choosing between a vanilla {@code PoiManager} reservation and a claim structure of
 * this mod's own).
 *
 * <p><b>Why a plain map, not a {@code PoiManager} reservation ticket:</b> reusing
 * {@code PoiManager.take}/{@code release} would mean registering every eligible Create seat as a
 * {@code PoiType} the way {@code villager_customers.shop.ShopPoi} does for table cloths
 * (`docs/spec/decisions/DEC-008-poi.md`) — sixteen colour variants of one block, tracked through the
 * world's saved POI index, for a claim that only ever needs to survive one walk
 * ({@link villager_customers.model.CustomerRules#WALK_TIMEOUT_TICKS}, reused here) and is explicitly
 * allowed to be lost on restart (`docs/spec/04-architecture.md` `ARCH-DEC-005`'s own accepted risk
 * for {@code CUSTOMER}'s trip memory already). A claim this short-lived buys nothing from POI
 * persistence and would cost a second {@code PoiType} registration and a second discovery path to
 * keep in sync with seats appearing and disappearing. A bare, non-persistent map from the seat's
 * {@link GlobalPos} to the claiming villager's {@link UUID} — with an expiry so a claim a villager
 * never releases (killed, force-removed, or a save lost mid-walk) cannot block that seat forever —
 * is the whole mechanism this ticket needs, exactly as its own Approach names as the fallback.
 *
 * <p>Package-private: only {@code KeeperSeatSearch} and {@code KeeperHooks} touch this directly.
 * Held in one static map across every {@link net.minecraft.server.level.ServerLevel} — safe because
 * {@link GlobalPos} already carries the dimension, the same convention
 * {@code villager_customers.customer.CustomerMemoryModules}'s own {@code GlobalPos} memory relies
 * on — and safe against concurrent access because every claim/read happens on the single server
 * thread while a villager's brain ticks, never off-thread.
 */
final class SeatClaims {
    private record Claim(UUID villagerId, long expiresAtGameTime) {
    }

    private static final Map<GlobalPos, Claim> CLAIMS = new ConcurrentHashMap<>();

    private SeatClaims() {
    }

    /** Whether {@code seat} is currently claimed by anyone (expired claims are pruned first). */
    static boolean isClaimed(GlobalPos seat, long gameTime) {
        Claim claim = CLAIMS.get(seat);
        if (claim == null) {
            return false;
        }
        if (claim.expiresAtGameTime() <= gameTime) {
            CLAIMS.remove(seat, claim);
            return false;
        }
        return true;
    }

    /**
     * Attempts to claim {@code seat} for {@code villagerId}, expiring at {@code expiresAtGameTime}.
     * Succeeds if nobody else holds an unexpired claim on it (re-claiming one's own claim, e.g. a
     * defensive re-check, always succeeds and refreshes the expiry).
     */
    static boolean tryClaim(GlobalPos seat, UUID villagerId, long gameTime, long expiresAtGameTime) {
        Claim existing = CLAIMS.get(seat);
        if (existing != null && existing.expiresAtGameTime() > gameTime && !existing.villagerId().equals(villagerId)) {
            return false;
        }
        CLAIMS.put(seat, new Claim(villagerId, expiresAtGameTime));
        return true;
    }

    /** Releases {@code seat}'s claim if it is still held by {@code villagerId} (a no-op otherwise). */
    static void release(GlobalPos seat, UUID villagerId) {
        CLAIMS.computeIfPresent(seat, (pos, claim) -> claim.villagerId().equals(villagerId) ? null : claim);
    }

    /** Test-only: clears every claim, so one game test never sees another's leftovers. */
    static void clearForTesting() {
        CLAIMS.clear();
    }
}
