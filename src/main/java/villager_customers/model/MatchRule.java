package villager_customers.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether a villager's offer and a table cloth mirror each other (`TRANSACTION-REQ-001`,
 * `TRANSACTION-REQ-008`; `decisions/DEC-005-one-mechanism.md`): the cloth's goods equal the offer's
 * cost stack and the cloth's price equals the offer's result stack, and an offer whose cost has a
 * second item never matches, regardless of the first.
 */
public final class MatchRule {
    private MatchRule() {
    }

    /**
     * @param offerCost the offer's first (and, when matched, only) cost stack
     * @param offerSecondCost the offer's second cost stack, if any; present means never matched
     *     (`TRANSACTION-REQ-008`)
     * @param offerResult the offer's result stack
     * @param goods the table cloth's configured goods; a match requires exactly one stack, equal to
     *     {@code offerCost}
     * @param clothPrice the table cloth's configured price stack
     * @return whether the offer and the cloth mirror each other (`TRANSACTION-REQ-001`)
     */
    public static boolean matches(
        StackShape offerCost, Optional<StackShape> offerSecondCost, StackShape offerResult, List<StackShape> goods,
        StackShape clothPrice
    ) {
        if (offerSecondCost.isPresent()) {
            return false;
        }
        if (goods.size() != 1) {
            return false;
        }
        return Objects.equals(goods.get(0), offerCost) && Objects.equals(clothPrice, offerResult);
    }
}
