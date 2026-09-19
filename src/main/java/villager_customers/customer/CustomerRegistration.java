package villager_customers.customer;

/**
 * This package's single entry point into {@code VillagerCustomers.onInitialize}
 * (`docs/spec/04-architecture.md` `ARCH-DEC-005`), mirroring {@code villager_customers.shop.ShopRegistration}.
 */
public final class CustomerRegistration {
    private CustomerRegistration() {
    }

    /**
     * Registers the shopping-trip memory modules. The behaviour itself is wired into
     * {@code Activity.WORK} by {@code villager_customers.mixin.VillagerBrainMixin}, not here.
     */
    public static void register() {
        CustomerMemoryModules.register();
    }
}
