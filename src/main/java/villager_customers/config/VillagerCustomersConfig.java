package villager_customers.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.model.CustomerRules;
import villager_customers.model.KeeperRules;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The mod's config values — {@code shop_search_radius}
 * (`docs/spec/decisions/DEC-010-village-wide-shop-search.md`, `CUSTOMER-REQ-003`),
 * {@code nitwit_breeding_chance} (`VC-19`, `docs/spec/decisions/DEC-011-nitwit-keepers.md`,
 * `KEEPER-REQ-001`) and {@code keeper_seek_cooldown_ticks} (`VC-20`, `docs/spec/domains/keeper.md`
 * `KEEPER-REQ-004`, added at the end per that ticket's own Build step 4) — read once at startup from
 * {@code villager_customers.properties} under Fabric's own config directory. Backed by
 * {@link Properties} — the JDK's own key/value file format — rather than a hand-rolled parser or a
 * new config-library dependency for a few clamped values
 * (`docs/spec/decisions/DEC-003-licence.md`'s dependency discipline).
 *
 * <p>{@link #load()} always (re)writes the file with the doc comment and the clamped values it is
 * about to use: an absent file gets created with the defaults, and an out-of-range or malformed
 * value found on disk is normalized on disk too, not just in memory, so a player who edits it to
 * something silly sees what actually took effect.
 */
public final class VillagerCustomersConfig {
    private static final String FILE_NAME = "villager_customers.properties";
    private static final String KEY_SHOP_SEARCH_RADIUS = "shop_search_radius";
    private static final String KEY_NITWIT_BREEDING_CHANCE = "nitwit_breeding_chance";
    private static final String KEY_KEEPER_SEEK_COOLDOWN_TICKS = "keeper_seek_cooldown_ticks";
    private static final String DOC_COMMENT =
        "villager_customers config\n" + "shop_search_radius: how far, in blocks horizontally, the shopping-trip search (`CUSTOMER-REQ-003`)\n"
            + "looks for a matching shop from the village's meeting point (or the villager's own position\n"
            + "when it remembers none). Clamped to " + CustomerRules.MIN_SHOP_SEARCH_RADIUS + ".."
            + CustomerRules.MAX_SHOP_SEARCH_RADIUS + "; default " + CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS + " (DEC-010).\n"
            + "nitwit_breeding_chance: the chance a bred baby villager becomes a nitwit instead of\n"
            + "vanilla's unconditional NONE (`KEEPER-REQ-001`). Clamped to " + KeeperRules.MIN_NITWIT_BREEDING_CHANCE + ".."
            + KeeperRules.MAX_NITWIT_BREEDING_CHANCE + "; default " + KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE + " (DEC-011).\n"
            + "keeper_seek_cooldown_ticks: how many ticks an adult nitwit waits between seeking a free\n"
            + "seat next to a keeperless stock ticker -- the same wait after a failed seek, an ejection or\n"
            + "a broken seat (`KEEPER-REQ-004`, `KEEPER-DEC-003`). Clamped to " + KeeperRules.MIN_SEEK_COOLDOWN_TICKS + ".."
            + KeeperRules.MAX_SEEK_COOLDOWN_TICKS + "; default " + KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS + " (VC-20).";

    private static final Logger LOGGER = LoggerFactory.getLogger("villager_customers");

    private static volatile int shopSearchRadius = CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS;
    private static volatile double nitwitBreedingChance = KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE;
    private static volatile int keeperSeekCooldownTicks = KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS;

    private VillagerCustomersConfig() {
    }

    /**
     * The configured search radius, already clamped to
     * {@code [}{@link CustomerRules#MIN_SHOP_SEARCH_RADIUS}{@code , }
     * {@link CustomerRules#MAX_SHOP_SEARCH_RADIUS}{@code ]}. {@link CustomerRules#DEFAULT_SHOP_SEARCH_RADIUS}
     * until {@link #load()} has run.
     */
    public static int shopSearchRadius() {
        return shopSearchRadius;
    }

    /**
     * The configured nitwit breeding chance, already clamped to
     * {@code [}{@link KeeperRules#MIN_NITWIT_BREEDING_CHANCE}{@code , }
     * {@link KeeperRules#MAX_NITWIT_BREEDING_CHANCE}{@code ]}. {@link KeeperRules#DEFAULT_NITWIT_BREEDING_CHANCE}
     * until {@link #load()} has run (`KEEPER-REQ-001`).
     */
    public static double nitwitBreedingChance() {
        return nitwitBreedingChance;
    }

    /**
     * The configured keeper seek cooldown, already clamped to
     * {@code [}{@link KeeperRules#MIN_SEEK_COOLDOWN_TICKS}{@code , }
     * {@link KeeperRules#MAX_SEEK_COOLDOWN_TICKS}{@code ]} (`VC-20`, `KEEPER-REQ-004`).
     * {@link KeeperRules#DEFAULT_SEEK_COOLDOWN_TICKS} until {@link #load()} has run.
     */
    public static int keeperSeekCooldownTicks() {
        return keeperSeekCooldownTicks;
    }

    /** Reads (and normalizes) the config file under Fabric's own config directory. Call once during mod init. */
    public static void load() {
        load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
    }

    /** Package-visible so a plain unit test can drive this against a real temp file, no running game needed. */
    static void load(Path path) {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException e) {
                LOGGER.warn("villager_customers could not read {}, using the default: {}", path, e.toString());
            }
        }
        int clampedRadius = CustomerRules.clampShopSearchRadius(
            parseInt(properties.getProperty(KEY_SHOP_SEARCH_RADIUS), KEY_SHOP_SEARCH_RADIUS, CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS)
        );
        double clampedChance = KeeperRules.clampNitwitBreedingChance(parseChance(properties.getProperty(KEY_NITWIT_BREEDING_CHANCE)));
        int clampedCooldown = KeeperRules.clampSeekCooldownTicks(
            parseInt(
                properties.getProperty(KEY_KEEPER_SEEK_COOLDOWN_TICKS), KEY_KEEPER_SEEK_COOLDOWN_TICKS, KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS
            )
        );
        shopSearchRadius = clampedRadius;
        nitwitBreedingChance = clampedChance;
        keeperSeekCooldownTicks = clampedCooldown;
        writeNormalized(path, clampedRadius, clampedChance, clampedCooldown);
    }

    private static int parseInt(String raw, String key, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("villager_customers: {} value \"{}\" is not a whole number; using the default", key, raw);
            return defaultValue;
        }
    }

    private static double parseChance(String raw) {
        if (raw == null) {
            return KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("villager_customers: {} value \"{}\" is not a number; using the default", KEY_NITWIT_BREEDING_CHANCE, raw);
            return KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE;
        }
    }

    private static void writeNormalized(Path path, int shopSearchRadiusValue, double nitwitBreedingChanceValue, int keeperSeekCooldownTicksValue) {
        Properties properties = new Properties();
        properties.setProperty(KEY_SHOP_SEARCH_RADIUS, String.valueOf(shopSearchRadiusValue));
        properties.setProperty(KEY_NITWIT_BREEDING_CHANCE, String.valueOf(nitwitBreedingChanceValue));
        properties.setProperty(KEY_KEEPER_SEEK_COOLDOWN_TICKS, String.valueOf(keeperSeekCooldownTicksValue));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, DOC_COMMENT);
            }
        } catch (IOException e) {
            LOGGER.warn("villager_customers could not write {}: {}", path, e.toString());
        }
    }

    /** Test-only: sets the in-memory radius directly, without touching disk (game tests). */
    public static void setShopSearchRadiusForTesting(int radius) {
        shopSearchRadius = radius;
    }

    /** Test-only: restores the compiled-in default (game tests). */
    public static void resetShopSearchRadiusForTesting() {
        shopSearchRadius = CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS;
    }

    /** Test-only: sets the in-memory nitwit breeding chance directly, without touching disk (game tests, `VC-19`). */
    public static void setNitwitBreedingChanceForTesting(double chance) {
        nitwitBreedingChance = chance;
    }

    /** Test-only: restores the compiled-in default (game tests, `VC-19`). */
    public static void resetNitwitBreedingChanceForTesting() {
        nitwitBreedingChance = KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE;
    }

    /** Test-only: sets the in-memory keeper seek cooldown directly, without touching disk (`VC-20`, game tests). */
    public static void setKeeperSeekCooldownTicksForTesting(int ticks) {
        keeperSeekCooldownTicks = ticks;
    }

    /** Test-only: restores the compiled-in default (`VC-20`, game tests). */
    public static void resetKeeperSeekCooldownTicksForTesting() {
        keeperSeekCooldownTicks = KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS;
    }
}
