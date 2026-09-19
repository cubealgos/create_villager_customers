package villager_customers.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** `TRANSACTION-REQ-010`. */
final class NuggetConversionTest {
    @Test
    void anExactMultipleOfTheNuggetValueDividesEvenly() {
        assertEquals(2, NuggetConversion.nuggets(6, 3));
    }

    @Test
    void aRemainderRoundsUpToTheNextNugget() {
        assertEquals(3, NuggetConversion.nuggets(7, 3));
    }

    @Test
    void oneXpBelowTheNuggetValueStillCostsOneNugget() {
        assertEquals(1, NuggetConversion.nuggets(1, 3));
    }

    @Test
    void zeroOfferXpNeedsNoNuggets() {
        assertEquals(0, NuggetConversion.nuggets(0, 3));
    }

    @Test
    void negativeOfferXpNeedsNoNuggets() {
        assertEquals(0, NuggetConversion.nuggets(-5, 3));
    }

    @Test
    void aNuggetValueBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NuggetConversion.nuggets(6, 0));
    }
}
