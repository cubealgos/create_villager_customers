package villager_customers.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** `TRANSACTION-REQ-001`, `TRANSACTION-REQ-008`. */
final class MatchRuleTest {
    private static final StackShape WHEAT_20 = new StackShape("minecraft:wheat", 20);
    private static final StackShape EMERALD_1 = new StackShape("minecraft:emerald", 1);

    @Test
    void goodsEqualToCostAndPriceEqualToResultMatches() {
        assertTrue(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(WHEAT_20), EMERALD_1));
    }

    @Test
    void goodsWithADifferentItemThanCostDoesNotMatch() {
        StackShape carrots20 = new StackShape("minecraft:carrot", 20);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(carrots20), EMERALD_1));
    }

    @Test
    void goodsWithADifferentCountThanCostDoesNotMatch() {
        StackShape wheat19 = new StackShape("minecraft:wheat", 19);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(wheat19), EMERALD_1));
    }

    @Test
    void priceWithADifferentItemThanResultDoesNotMatch() {
        StackShape diamond1 = new StackShape("minecraft:diamond", 1);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(WHEAT_20), diamond1));
    }

    @Test
    void priceWithADifferentCountThanResultDoesNotMatch() {
        StackShape emerald2 = new StackShape("minecraft:emerald", 2);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(WHEAT_20), emerald2));
    }

    @Test
    void anOfferWithASecondCostNeverMatchesRegardlessOfTheFirst() {
        StackShape secondCost = new StackShape("minecraft:stick", 1);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.of(secondCost), EMERALD_1, List.of(WHEAT_20), EMERALD_1));
    }

    @Test
    void goodsWithMoreThanOneStackNeverMatches() {
        StackShape stick1 = new StackShape("minecraft:stick", 1);
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(WHEAT_20, stick1), EMERALD_1));
    }

    @Test
    void emptyGoodsNeverMatches() {
        assertFalse(MatchRule.matches(WHEAT_20, Optional.empty(), EMERALD_1, List.of(), EMERALD_1));
    }
}
