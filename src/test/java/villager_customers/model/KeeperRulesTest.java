package villager_customers.model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nitwit breeding roll given a fixed random source (`docs/spec/operations/testing.md`'s "chance
 * roll given a fixed random source"; `docs/spec/domains/keeper.md` `KEEPER-REQ-001`), and the
 * {@code nitwit_breeding_chance} clamp (`docs/spec/decisions/DEC-011-nitwit-keepers.md`).
 */
class KeeperRulesTest {
    @Test
    void rollsBelowTheChanceSucceed() {
        assertTrue(KeeperRules.rollsNitwit(0.0, KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE));
        assertTrue(KeeperRules.rollsNitwit(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE - 0.0001, KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE));
    }

    @Test
    void rollsAtOrAboveTheChanceFail() {
        assertFalse(KeeperRules.rollsNitwit(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE, KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE));
        assertFalse(KeeperRules.rollsNitwit(0.9999, KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE));
    }

    @Test
    void aChanceOfOneAlwaysSucceeds() {
        assertTrue(KeeperRules.rollsNitwit(0.0, 1.0));
        assertTrue(KeeperRules.rollsNitwit(0.9999999, 1.0));
    }

    @Test
    void aChanceOfZeroNeverSucceeds() {
        assertFalse(KeeperRules.rollsNitwit(0.0, 0.0));
        assertFalse(KeeperRules.rollsNitwit(0.9999999, 0.0));
    }

    /**
     * The default chance (10%) is bounded over a large, seeded sample — mirrors what the game test
     * suite proves end-to-end against the real mixin (`VC-19`'s Approach: "a statistical test over
     * 200 spawns with a seeded random"), done here instead as a pure unit test since the roll itself
     * needs no Minecraft class at all. A generous band (3%..20% of 10,000 rolls) keeps this
     * non-flaky while still catching a grossly wrong comparison (e.g. {@code <=} instead of
     * {@code <}, or an inverted roll).
     */
    @Test
    void theDefaultChanceIsStatisticallyBoundedOverManyRolls() {
        Random random = new Random(42);
        int rolls = 10_000;
        int successes = 0;
        for (int i = 0; i < rolls; i++) {
            if (KeeperRules.rollsNitwit(random.nextDouble(), KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE)) {
                successes++;
            }
        }
        double rate = successes / (double) rolls;
        assertTrue(rate > 0.03, "success rate should be well above 3%: " + rate);
        assertTrue(rate < 0.20, "success rate should be well below 20%: " + rate);
    }

    @Test
    void aChanceWithinBoundsPassesThroughUnchanged() {
        assertEquals(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE));
        assertEquals(KeeperRules.MIN_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(KeeperRules.MIN_NITWIT_BREEDING_CHANCE));
        assertEquals(KeeperRules.MAX_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(KeeperRules.MAX_NITWIT_BREEDING_CHANCE));
    }

    @Test
    void aChanceBelowTheMinimumClampsUp() {
        assertEquals(KeeperRules.MIN_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(-0.5));
        assertEquals(KeeperRules.MIN_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(-100.0));
    }

    @Test
    void aChanceAboveTheMaximumClampsDown() {
        assertEquals(KeeperRules.MAX_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(1.5));
        assertEquals(KeeperRules.MAX_NITWIT_BREEDING_CHANCE, KeeperRules.clampNitwitBreedingChance(100.0));
    }
}
