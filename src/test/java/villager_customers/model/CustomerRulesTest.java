package villager_customers.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chance roll given a fixed random source (`docs/spec/operations/testing.md`'s "Unit" layer;
 * `docs/spec/domains/customer.md` `CUSTOMER-REQ-002`), and the {@code shop_search_radius} clamp
 * (`CUSTOMER-REQ-003`, `docs/spec/decisions/DEC-010-village-wide-shop-search.md`, `VC-18`).
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

    @Test
    void aRadiusWithinBoundsPassesThroughUnchanged() {
        assertEquals(CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS));
        assertEquals(CustomerRules.MIN_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(CustomerRules.MIN_SHOP_SEARCH_RADIUS));
        assertEquals(CustomerRules.MAX_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(CustomerRules.MAX_SHOP_SEARCH_RADIUS));
    }

    @Test
    void aRadiusBelowTheMinimumClampsUp() {
        assertEquals(CustomerRules.MIN_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(0));
        assertEquals(CustomerRules.MIN_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(-100));
        assertEquals(CustomerRules.MIN_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(CustomerRules.MIN_SHOP_SEARCH_RADIUS - 1));
    }

    @Test
    void aRadiusAboveTheMaximumClampsDown() {
        assertEquals(CustomerRules.MAX_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(CustomerRules.MAX_SHOP_SEARCH_RADIUS + 1));
        assertEquals(CustomerRules.MAX_SHOP_SEARCH_RADIUS, CustomerRules.clampShopSearchRadius(10_000));
    }
}
