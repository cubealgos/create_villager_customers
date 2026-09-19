package villager_customers.model;

/**
 * The xp-nugget conversion: a completed unit credits the shop with the offer's xp, rounded up to
 * whole {@code create:experience_nugget}s (`TRANSACTION-REQ-010`; `decisions/DEC-007-xp-nuggets.md`).
 */
public final class NuggetConversion {
    private NuggetConversion() {
    }

    /**
     * @param offerXp the offer's own xp value; non-positive yields zero nuggets
     * @param nuggetXp the xp one nugget is worth; must be at least 1
     * @return the offer's xp divided by {@code nuggetXp}, rounded up
     */
    public static int nuggets(int offerXp, int nuggetXp) {
        if (nuggetXp < 1) {
            throw new IllegalArgumentException("nuggetXp must be at least 1, was " + nuggetXp);
        }
        if (offerXp <= 0) {
            return 0;
        }
        return (offerXp + nuggetXp - 1) / nuggetXp;
    }
}
