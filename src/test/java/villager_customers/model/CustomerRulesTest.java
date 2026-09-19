package villager_customers.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chance roll given a fixed random source (`docs/spec/operations/testing.md`'s "Unit" layer;
 * `docs/spec/domains/customer.md` `CUSTOMER-REQ-002`).
 */
class CustomerRulesTest {
    @Test
    void rollsBelowTheChanceSucceed() {
        assertTrue(CustomerRules.rolls(0.0));
        assertTrue(CustomerRules.rolls(CustomerRules.CHANCE_PER_RESTOCK - 0.0001));
    }

    @Test
    void rollsAtOrAboveTheChanceFail() {
        assertFalse(CustomerRules.rolls(CustomerRules.CHANCE_PER_RESTOCK));
        assertFalse(CustomerRules.rolls(0.9999));
    }
}
