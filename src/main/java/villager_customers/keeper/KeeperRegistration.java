package villager_customers.keeper;

/**
 * This package's single entry point into {@code VillagerCustomers.onInitialize}
 * (`docs/spec/04-architecture.md` `ARCH-DEC-006`), mirroring {@code villager_customers.customer.CustomerRegistration}.
 */
public final class KeeperRegistration {
    private KeeperRegistration() {
    }

    /**
     * Registers the keeper-seek memory modules. The behaviour itself is wired into
     * {@code Activity.IDLE} by {@code villager_customers.mixin.VillagerBrainMixin}, not here.
     */
    public static void register() {
        KeeperMemoryModules.register();
    }
}
