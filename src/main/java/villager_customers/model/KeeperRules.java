package villager_customers.model;

/**
 * The pure constants behind a nitwit keeper's seek cadence (`docs/spec/domains/keeper.md`
 * `KEEPER-REQ-004`; `KEEPER-DEC-003`). No Minecraft, Fabric or Create import, mirroring
 * {@link CustomerRules}'s own convention. Unlike {@code CUSTOMER}, the seek itself is never chance-
 * gated (`KEEPER-DEC-002`), so this class carries no {@code rolls(double)}-shaped predicate — only
 * the one cooldown value's default and clamp range, reused for every wait `KEEPER-DEC-003` names:
 * the interval between seeks while idle, the wait after a failed seek, and the wait after ejection
 * or a broken seat.
 */
public final class KeeperRules {
    /** The default {@code keeper_seek_cooldown_ticks}: one vanilla day, matching {@code CUSTOMER}'s own daily restock cadence (`KEEPER-DEC-003`). */
    public static final int DEFAULT_SEEK_COOLDOWN_TICKS = 24000;

    /** The lowest {@code keeper_seek_cooldown_ticks} the config accepts; smaller values clamp up to this. */
    public static final int MIN_SEEK_COOLDOWN_TICKS = 200;

    /** The highest {@code keeper_seek_cooldown_ticks} the config accepts; larger values clamp down to this. */
    public static final int MAX_SEEK_COOLDOWN_TICKS = 100000;

    private KeeperRules() {
    }

    /**
     * {@code ticks} clamped to {@code [}{@link #MIN_SEEK_COOLDOWN_TICKS}{@code , }
     * {@link #MAX_SEEK_COOLDOWN_TICKS}{@code ]}, the same clamp shape
     * {@link CustomerRules#clampShopSearchRadius(int)} already uses for {@code shop_search_radius}.
     */
    public static int clampSeekCooldownTicks(int ticks) {
        return Math.min(MAX_SEEK_COOLDOWN_TICKS, Math.max(MIN_SEEK_COOLDOWN_TICKS, ticks));
    }
}
