package villager_customers.transaction;

/**
 * Marks the current thread as running a mod-driven trade unit, for
 * {@code villager_customers.mixin.VillagerRewardTradeXpMixin} to key off of: only while this flag is
 * set does the mixin suppress vanilla's trade-xp orb (`docs/spec/domains/transaction.md`
 * `TRANSACTION-REQ-010`; `decisions/DEC-007-xp-nuggets.md`; VC-12).
 *
 * <p>{@link TransactionExecutor#execute} sets the flag around each {@code villager.notifyTrade(offer)}
 * call and clears it in a {@code finally} block, so the flag never leaks past that one call. A
 * player's own trade never touches this class, so {@code isActive()} stays {@code false} for it and
 * its orb still spawns.
 *
 * <p>A {@link ThreadLocal} rather than a plain static field: server game logic runs on one thread in
 * practice, but nothing here depends on that — a thread-local flag cannot leak the "mod-driven" state
 * into a concurrent player trade on another thread the way a shared static boolean could.
 */
public final class ModDrivenTrade {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ModDrivenTrade() {
    }

    /** Marks the current thread as running a mod-driven trade unit. */
    static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    /** Clears the mark; always called from a {@code finally} block by whoever called {@link #begin()}. */
    static void end() {
        ACTIVE.set(Boolean.FALSE);
    }

    /** Whether the current thread is inside a mod-driven trade unit right now. */
    public static boolean isActive() {
        return ACTIVE.get();
    }
}
