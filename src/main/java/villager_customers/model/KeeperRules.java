package villager_customers.model;

/**
 * The pure constants and decision rules behind the {@code KEEPER} domain
 * (`docs/spec/domains/keeper.md`; `docs/spec/decisions/DEC-011-nitwit-keepers.md`): how a bred baby
 * villager becomes a nitwit instead of vanilla's unconditional {@code NONE} (`KEEPER-REQ-001`), and
 * the cadence an adult nitwit's own seat seek runs on (`KEEPER-REQ-004`; `KEEPER-DEC-003`). No
 * Minecraft, Fabric or Create import, mirroring {@link CustomerRules}'s own convention.
 * {@link #rollsNitwit(double, double)} is unit-testable without a game test
 * (`docs/spec/operations/testing.md`'s "chance roll given a fixed random source") — the mixin-side
 * caller ({@code villager_customers.mixin.VillagerBreedingMixin}) supplies the level's own random
 * and the configured chance; this class only decides the boolean outcome and the clamp. The seek
 * itself is never chance-gated (`KEEPER-DEC-002`), so it carries no {@code rolls(double)}-shaped
 * predicate of its own — only the one cooldown value's default and clamp range, reused for every
 * wait `KEEPER-DEC-003` names: the interval between seeks while idle, the wait after a failed seek,
 * and the wait after ejection or a broken seat.
 */
public final class KeeperRules {
    /** The default chance a bred baby becomes a nitwit, ten percent (`DEC-011`). */
    public static final double DEFAULT_NITWIT_BREEDING_CHANCE = 0.10;

    /** The lowest {@code nitwit_breeding_chance} the config accepts. */
    public static final double MIN_NITWIT_BREEDING_CHANCE = 0.0;

    /** The highest {@code nitwit_breeding_chance} the config accepts. */
    public static final double MAX_NITWIT_BREEDING_CHANCE = 1.0;

    /** The default {@code keeper_seek_cooldown_ticks}: one vanilla day, matching {@code CUSTOMER}'s own daily restock cadence (`KEEPER-DEC-003`). */
    public static final int DEFAULT_SEEK_COOLDOWN_TICKS = 24000;

    /** The lowest {@code keeper_seek_cooldown_ticks} the config accepts; smaller values clamp up to this. */
    public static final int MIN_SEEK_COOLDOWN_TICKS = 200;

    /** The highest {@code keeper_seek_cooldown_ticks} the config accepts; larger values clamp down to this. */
    public static final int MAX_SEEK_COOLDOWN_TICKS = 100000;

    private KeeperRules() {
    }

    /**
     * Whether one roll of {@code random} — expected uniform in {@code [0, 1)}, the range
     * {@link java.util.Random#nextDouble()} and {@code RandomSource.nextDouble()} both produce —
     * succeeds against {@code chance} (`KEEPER-REQ-001`). {@code chance <= 0.0} therefore never
     * succeeds and {@code chance >= 1.0} always does, matching the config's own clamp bounds.
     */
    public static boolean rollsNitwit(double random, double chance) {
        return random < chance;
    }

    /**
     * {@code chance} clamped to {@code [}{@link #MIN_NITWIT_BREEDING_CHANCE}{@code , }
     * {@link #MAX_NITWIT_BREEDING_CHANCE}{@code ]} (`SURFACE-REQ-002`-style), applied to whatever
     * value {@code villager_customers.config.VillagerCustomersConfig} reads from disk.
     */
    public static double clampNitwitBreedingChance(double chance) {
        return Math.min(MAX_NITWIT_BREEDING_CHANCE, Math.max(MIN_NITWIT_BREEDING_CHANCE, chance));
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
