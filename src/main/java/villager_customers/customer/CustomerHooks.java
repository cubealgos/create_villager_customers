package villager_customers.customer;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.shop.Shop;
import villager_customers.shop.ShopSearch;
import villager_customers.transaction.TransactionExecutor;

import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * The restock hook {@code villager_customers.mixin.VillagerBrainMixin} calls
 * (`docs/spec/domains/customer.md` `CUSTOMER-REQ-001`, `002`, `003`, `008`, `009`;
 * `docs/spec/contracts/data-contract.md` `DATA-REQ-002`).
 */
public final class CustomerHooks {
    /**
     * The chance roll's random source: {@link Math#random()} in production, overridable in game
     * tests for a forced roll (`docs/spec/operations/testing.md`). Deliberately {@code public} —
     * this mod's own game tests live in a different package ({@code villager_customers.gametest})
     * and this is the ticket's documented test seam, kept as small as a plain field setter.
     */
    private static DoubleSupplier rollSource = Math::random;

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
     * On success, {@code ShopSearch} is queried for the nearest shop matching any offer with uses
     * left; no match leaves the villager idle, no error, no retry before the next restock
     * (`CUSTOMER-REQ-003`, `009`).
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
        if (!CustomerRules.rolls(rollSource.getAsDouble())) {
            return; // CUSTOMER-REQ-002: rolled and failed; nothing until the next restock
        }

        Optional<Shop> match = ShopSearch.matching(level, villager.blockPosition(), ShopSearch.VILLAGE_REACH, shop -> matchesAnyOffer(villager, shop));
        match.ifPresent(
            shop -> brain.setMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET, GlobalPos.of(level.dimension(), shop.pos()))
        );
    }

    private static boolean matchesAnyOffer(Villager villager, Shop shop) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getUses() < offer.getMaxUses() && TransactionExecutor.matches(offer, shop)) {
                return true;
            }
        }
        return false;
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
