package villager_customers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.config.VillagerCustomersConfig;
import villager_customers.customer.CustomerRegistration;
import villager_customers.debug.DebugCommand;
import villager_customers.keeper.KeeperRegistration;
import villager_customers.shop.ShopRegistration;

/** The mod's server-and-common entrypoint. */
public final class VillagerCustomers implements ModInitializer {
    public static final String MOD_ID = "villager_customers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VillagerCustomersConfig.load();
        ShopRegistration.register();
        CustomerRegistration.register();
        KeeperRegistration.register();
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) DebugCommand.register();
        LOGGER.info("Villager Customers ready beside Create Fly");
    }
}
