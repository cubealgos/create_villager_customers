package villager_customers.shop;

/**
 * This package's single entry point into {@code VillagerCustomers.onInitialize}
 * (`docs/spec/decisions/DEC-008-poi.md`).
 */
public final class ShopRegistration {
    private ShopRegistration() {
    }

    /** Registers the shop point-of-interest type and its world sync. */
    public static void register() {
        ShopPoi.register();
    }
}
