package villager_customers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's server-and-common entrypoint. */
public final class VillagerCustomers implements ModInitializer {
    public static final String MOD_ID = "villager_customers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Villager Customers ready beside Create Fly");
    }
}
