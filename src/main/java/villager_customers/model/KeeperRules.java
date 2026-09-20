package villager_customers.model;

/**
 * The pure decision rule behind whether a bred baby villager becomes a nitwit instead of vanilla's
 * unconditional {@code NONE} (`docs/spec/domains/keeper.md` `KEEPER-REQ-001`;
 * `docs/spec/decisions/DEC-011-nitwit-keepers.md`). No Minecraft, Fabric or Create import, so
 * {@link #rollsNitwit(double, double)} is unit-testable without a game test
 * (`docs/spec/operations/testing.md`'s "chance roll given a fixed random source"), mirroring
 * {@link CustomerRules#rolls(double)}'s own seam. The mixin-side caller
 * ({@code villager_customers.mixin.VillagerBreedingMixin}) supplies the level's own random and the
 * configured chance; this class only decides the boolean outcome and the clamp.
 */
public final class KeeperRules {
    /** The default chance a bred baby becomes a nitwit, ten percent (`DEC-011`). */
    public static final double DEFAULT_NITWIT_BREEDING_CHANCE = 0.10;

    /** The lowest {@code nitwit_breeding_chance} the config accepts. */
    public static final double MIN_NITWIT_BREEDING_CHANCE = 0.0;

    /** The highest {@code nitwit_breeding_chance} the config accepts. */
    public static final double MAX_NITWIT_BREEDING_CHANCE = 1.0;

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
}
