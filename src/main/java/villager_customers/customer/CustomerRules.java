package villager_customers.customer;

/**
 * The pure decision rules behind a shopping trip (`docs/spec/domains/customer.md` `CUSTOMER-REQ-002`,
 * `CUSTOMER-REQ-004`, `CUSTOMER-REQ-007`; `docs/spec/decisions/DEC-009-chance-per-restock.md`). No
 * Minecraft, Fabric or Create import, so {@link #rolls(double)} is unit-testable without a game test
 * (`docs/spec/operations/testing.md`'s "chance roll given a fixed random source"), even though this
 * ticket places it in {@code villager_customers.customer} rather than the build's
 * {@code verifyPurePackage}-enforced {@code villager_customers.model} — see this ticket's own
 * Findings in its Constraints section.
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
}
