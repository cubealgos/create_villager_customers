package villager_customers.customer;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.model.CustomerRules;
import villager_customers.shop.Shop;
import villager_customers.shop.ShopSearch;
import villager_customers.transaction.TransactionExecutor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * The restock hook {@code villager_customers.mixin.VillagerBrainMixin} calls
 * (`docs/spec/domains/customer.md` `CUSTOMER-REQ-001`, `002`, `003`, `008`, `009`;
 * `docs/spec/contracts/data-contract.md` `DATA-REQ-002`), and the read-only search it runs, shared
 * with {@code villager_customers.debug.DebugCommand}'s {@code search} and {@code trip} subcommands
 * (`docs/spec/operations/testing.md`'s "Development tool" row, `VC-5`).
 */
public final class CustomerHooks {
    /**
     * The chance roll's random source: {@link Math#random()} in production, overridable in game
     * tests for a forced roll (`docs/spec/operations/testing.md`). Deliberately {@code public} —
     * this mod's own game tests live in a different package ({@code villager_customers.gametest})
     * and this is the ticket's documented test seam, kept as small as a plain field setter.
     */
    private static DoubleSupplier rollSource = Math::random;

    /**
     * Villager UUIDs whose next {@link #onRestock} roll is forced to succeed, consumed once on use
     * (`VC-5`'s {@code /villager_customers debug roll <villager>}). Dev-only state: never saved to
     * the world, never populated outside a development environment, since the debug command that
     * populates it is itself only registered there.
     */
    private static final Set<UUID> forcedRestockRolls = new HashSet<>();

    private CustomerHooks() {
    }

    /**
     * Called once per completed restock, from the mixin injected at {@code Villager.restock()}'s
     * {@code RETURN} (`CUSTOMER-REQ-002`). The whole mechanism — roll, search, remembering a target —
     * only operates while the villager's current activity is {@code WORK}
     * (`CUSTOMER-REQ-001` read together with this ticket's own night game test: a restock while
     * resting must leave no trip memory behind, not merely fail to walk). Within that gate: at most
     * one active trip (`CUSTOMER-REQ-008`) and no roll while on cooldown (`DATA-REQ-002`) are both
     * checked before the chance is rolled at all, so neither consumes a roll it would then ignore.
     * A pending forced roll (`VC-5`) short-circuits the chance roll itself, consumed here whether or
     * not a match is then found. On success, {@link #search} is queried for the nearest shop matching
     * any offer with uses left; no match leaves the villager idle, no error, no retry before the next
     * restock (`CUSTOMER-REQ-003`, `009`).
     */
    public static void onRestock(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(Activity.WORK)) {
            return;
        }
        if (brain.hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET)) {
            return; // CUSTOMER-REQ-008: at most one active trip
        }
        long gameTime = level.getGameTime();
        Optional<Long> cooldown = brain.getMemory(CustomerMemoryModules.SHOPPING_COOLDOWN);
        if (cooldown.isPresent() && gameTime < cooldown.get()) {
            return; // DATA-REQ-002: no roll while on cooldown
        }
        boolean forced = forcedRestockRolls.remove(villager.getUUID());
        if (!forced && !CustomerRules.rolls(rollSource.getAsDouble())) {
            return; // CUSTOMER-REQ-002: rolled and failed; nothing until the next restock
        }

        Search result = search(level, villager);
        result.shop().ifPresent(
            shop -> brain.setMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET, GlobalPos.of(level.dimension(), shop.pos()))
        );
    }

    /**
     * Every offer of {@code villager}'s that still has uses left, in trade order
     * (`CUSTOMER-REQ-003`, `009`).
     */
    public static List<MerchantOffer> eligibleOffers(Villager villager) {
        List<MerchantOffer> eligible = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getUses() < offer.getMaxUses()) {
                eligible.add(offer);
            }
        }
        return eligible;
    }

    /** The first of {@code villager}'s eligible offers (see {@link #eligibleOffers}) that matches {@code shop}, if any. */
    public static Optional<MerchantOffer> matchingOffer(Villager villager, Shop shop) {
        for (MerchantOffer offer : eligibleOffers(villager)) {
            if (TransactionExecutor.matches(offer, shop)) {
                return Optional.of(offer);
            }
        }
        return Optional.empty();
    }

    /**
     * The same eligible-offer, nearest-shop search {@link #onRestock} runs (`CUSTOMER-REQ-003`,
     * `009`), exposed read-only for {@code villager_customers.debug.DebugCommand}'s {@code search}
     * and {@code trip} subcommands (`VC-5`). Never mutates any state.
     */
    public static Search search(ServerLevel level, Villager villager) {
        if (eligibleOffers(villager).isEmpty()) {
            return new Search(Optional.empty(), Optional.empty(), Search.Reason.NO_OFFER_WITH_USES_LEFT);
        }
        Optional<Shop> shop = ShopSearch.matching(
            level, villager.blockPosition(), ShopSearch.VILLAGE_REACH, candidate -> matchingOffer(villager, candidate).isPresent()
        );
        if (shop.isEmpty()) {
            return new Search(Optional.empty(), Optional.empty(), Search.Reason.NO_SHOP_MATCHING_OFFER);
        }
        return new Search(shop, matchingOffer(villager, shop.get()), Search.Reason.MATCH);
    }

    /**
     * The outcome of {@link #search}: a matched shop and offer ({@code reason() == MATCH}, both
     * present), or which of the two eligibility checks failed (both empty).
     */
    public record Search(Optional<Shop> shop, Optional<MerchantOffer> offer, Reason reason) {
        public enum Reason {
            /** A reachable shop was found matching one of the villager's eligible offers. */
            MATCH,
            /** The villager has no offer with uses left at all. */
            NO_OFFER_WITH_USES_LEFT,
            /** The villager has an eligible offer, but no reachable shop matches any of them. */
            NO_SHOP_MATCHING_OFFER
        }
    }

    /** Test/dev-only: forces the next {@link #onRestock} roll to succeed for one villager, consumed once (`VC-5`). */
    public static void forceNextRoll(UUID villagerId) {
        forcedRestockRolls.add(villagerId);
    }

    /** Test/dev-only: whether a forced roll is still pending for a villager (`VC-5`'s game test accessor). */
    public static boolean hasForcedRoll(UUID villagerId) {
        return forcedRestockRolls.contains(villagerId);
    }

    /** Test-only: forces the next roll(s)' source. */
    public static void setRollSourceForTesting(DoubleSupplier source) {
        rollSource = source;
    }

    /** Test-only: restores the production roll source ({@link Math#random()}). */
    public static void resetRollSourceForTesting() {
        rollSource = Math::random;
    }
}
