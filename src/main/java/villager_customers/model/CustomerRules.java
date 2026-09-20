package villager_customers.model;

/**
 * The pure decision rules behind a shopping trip (`docs/spec/domains/customer.md` `CUSTOMER-REQ-002`,
 * `CUSTOMER-REQ-004`, `CUSTOMER-REQ-007`; `docs/spec/decisions/DEC-009-chance-per-restock.md`). No
 * Minecraft, Fabric or Create import, so {@link #rolls(double)} is unit-testable without a game test
 * (`docs/spec/operations/testing.md`'s "chance roll given a fixed random source"); the test seam that
 * forces a roll in game tests stays in {@code villager_customers.customer.CustomerHooks}, the only
 * caller that needs the game to exist at all.
 */
public final class CustomerRules {
    /** The chance a restock sends the villager shopping, default 50% (`DEC-009`). */
    public static final double CHANCE_PER_RESTOCK = 0.5;

    /** The walk's arrival distance, in blocks (`CUSTOMER-REQ-004`). */
    public static final int ARRIVAL_DISTANCE = 2;

    /** The walk's own give-up timeout, in ticks (`CUSTOMER-REQ-004`). */
    public static final int WALK_TIMEOUT_TICKS = 2400;

    /** The cooldown started after a cancelled or timed-out trip, in ticks (`CUSTOMER-REQ-007`). */
    public static final int COOLDOWN_TICKS = 2400;

    /**
     * The default shop-search radius, in blocks horizontally from the search origin — the village's
     * meeting point (bell) when the villager remembers one, else its own position
     * (`CUSTOMER-REQ-003`, amended by `docs/spec/decisions/DEC-010-village-wide-shop-search.md`).
     * Overridable through the mod's config as {@code shop_search_radius}
     * ({@code villager_customers.config.VillagerCustomersConfig}).
     */
    public static final int DEFAULT_SHOP_SEARCH_RADIUS = 128;

    /** The lowest {@code shop_search_radius} the config accepts; smaller values clamp up to this (`DEC-010`). */
    public static final int MIN_SHOP_SEARCH_RADIUS = 16;

    /** The highest {@code shop_search_radius} the config accepts; larger values clamp down to this (`DEC-010`). */
    public static final int MAX_SHOP_SEARCH_RADIUS = 256;

    private CustomerRules() {
    }

    /**
     * Whether one roll of {@code random} — expected uniform in {@code [0, 1)}, the range both
     * {@link Math#random()} and {@link java.util.Random#nextDouble()} produce — succeeds against
     * {@link #CHANCE_PER_RESTOCK} (`CUSTOMER-REQ-002`).
     */
    public static boolean rolls(double random) {
        return random < CHANCE_PER_RESTOCK;
    }

    /**
     * {@code radius} clamped to {@code [}{@link #MIN_SHOP_SEARCH_RADIUS}{@code , }
     * {@link #MAX_SHOP_SEARCH_RADIUS}{@code ]} (`DEC-010`'s config clamp, applied to whatever value
     * {@code villager_customers.config.VillagerCustomersConfig} reads from disk).
     */
    public static int clampShopSearchRadius(int radius) {
        return Math.min(MAX_SHOP_SEARCH_RADIUS, Math.max(MIN_SHOP_SEARCH_RADIUS, radius));
    }
}
