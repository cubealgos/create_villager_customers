package villager_customers.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import villager_customers.model.CustomerRules;
import villager_customers.model.KeeperRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VillagerCustomersConfig#load(Path)} against a real file in a temp directory — no running
 * game needed, since the package-visible {@code load(Path)} overload takes the file to read
 * directly rather than resolving it through {@code FabricLoader} (`VC-18`,
 * `docs/spec/decisions/DEC-010-village-wide-shop-search.md`).
 */
class VillagerCustomersConfigTest {
    @AfterEach
    void resetInMemoryValue() {
        VillagerCustomersConfig.resetShopSearchRadiusForTesting();
        VillagerCustomersConfig.resetNitwitBreedingChanceForTesting();
        VillagerCustomersConfig.resetKeeperSeekCooldownTicksForTesting();
    }

    @Test
    void anAbsentFileIsCreatedWithTheDefault(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        assertTrue(Files.notExists(path));

        VillagerCustomersConfig.load(path);

        assertEquals(CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, VillagerCustomersConfig.shopSearchRadius());
        assertEquals(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE, VillagerCustomersConfig.nitwitBreedingChance());
        assertEquals(KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS, VillagerCustomersConfig.keeperSeekCooldownTicks());
        assertTrue(Files.isRegularFile(path), "the file is created on first load");
        String written = Files.readString(path);
        assertTrue(written.contains("shop_search_radius=" + CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS), "the written file holds the default: " + written);
        assertTrue(written.contains("shop_search_radius:"), "the written file carries a doc comment: " + written);
        assertTrue(
            written.contains("nitwit_breeding_chance=" + KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE),
            "the written file holds the nitwit breeding chance default: " + written
        );
        assertTrue(written.contains("nitwit_breeding_chance:"), "the written file carries a doc comment for the nitwit breeding chance: " + written);
        assertTrue(
            written.contains("keeper_seek_cooldown_ticks=" + KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS),
            "the written file holds the keeper cooldown default: " + written
        );
        assertTrue(written.contains("keeper_seek_cooldown_ticks:"), "the written file carries a doc comment for the keeper cooldown: " + written);
    }

    @Test
    void aValueWithinBoundsIsReadAsIs(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "shop_search_radius=64\n");

        VillagerCustomersConfig.load(path);

        assertEquals(64, VillagerCustomersConfig.shopSearchRadius());
    }

    @Test
    void aValueBelowTheMinimumIsClampedUpOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "shop_search_radius=1\n");

        VillagerCustomersConfig.load(path);

        assertEquals(CustomerRules.MIN_SHOP_SEARCH_RADIUS, VillagerCustomersConfig.shopSearchRadius());
        String written = Files.readString(path);
        assertTrue(written.contains("shop_search_radius=" + CustomerRules.MIN_SHOP_SEARCH_RADIUS), "the clamped value is written back: " + written);
    }

    @Test
    void aValueAboveTheMaximumIsClampedDownOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "shop_search_radius=99999\n");

        VillagerCustomersConfig.load(path);

        assertEquals(CustomerRules.MAX_SHOP_SEARCH_RADIUS, VillagerCustomersConfig.shopSearchRadius());
        String written = Files.readString(path);
        assertTrue(written.contains("shop_search_radius=" + CustomerRules.MAX_SHOP_SEARCH_RADIUS), "the clamped value is written back: " + written);
    }

    @Test
    void aMalformedValueFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "shop_search_radius=not-a-number\n");

        VillagerCustomersConfig.load(path);

        assertEquals(CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, VillagerCustomersConfig.shopSearchRadius());
    }

    @Test
    void aMissingKeyFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "# no key here\n");

        VillagerCustomersConfig.load(path);

        assertEquals(CustomerRules.DEFAULT_SHOP_SEARCH_RADIUS, VillagerCustomersConfig.shopSearchRadius());
        assertEquals(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE, VillagerCustomersConfig.nitwitBreedingChance());
    }

    @Test
    void aNitwitBreedingChanceWithinBoundsIsReadAsIs(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "nitwit_breeding_chance=0.25\n");

        VillagerCustomersConfig.load(path);

        assertEquals(0.25, VillagerCustomersConfig.nitwitBreedingChance());
    }

    @Test
    void aNitwitBreedingChanceBelowTheMinimumIsClampedUpOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "nitwit_breeding_chance=-0.5\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.MIN_NITWIT_BREEDING_CHANCE, VillagerCustomersConfig.nitwitBreedingChance());
        String written = Files.readString(path);
        assertTrue(
            written.contains("nitwit_breeding_chance=" + KeeperRules.MIN_NITWIT_BREEDING_CHANCE), "the clamped value is written back: " + written
        );
    }

    @Test
    void aNitwitBreedingChanceAboveTheMaximumIsClampedDownOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "nitwit_breeding_chance=5\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.MAX_NITWIT_BREEDING_CHANCE, VillagerCustomersConfig.nitwitBreedingChance());
        String written = Files.readString(path);
        assertTrue(
            written.contains("nitwit_breeding_chance=" + KeeperRules.MAX_NITWIT_BREEDING_CHANCE), "the clamped value is written back: " + written
        );
    }

    @Test
    void aMalformedNitwitBreedingChanceFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "nitwit_breeding_chance=not-a-number\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.DEFAULT_NITWIT_BREEDING_CHANCE, VillagerCustomersConfig.nitwitBreedingChance());
    }

    @Test
    void aKeeperCooldownWithinBoundsIsReadAsIs(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "keeper_seek_cooldown_ticks=5000\n");

        VillagerCustomersConfig.load(path);

        assertEquals(5000, VillagerCustomersConfig.keeperSeekCooldownTicks());
    }

    @Test
    void aKeeperCooldownBelowTheMinimumIsClampedUpOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "keeper_seek_cooldown_ticks=1\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.MIN_SEEK_COOLDOWN_TICKS, VillagerCustomersConfig.keeperSeekCooldownTicks());
        String written = Files.readString(path);
        assertTrue(
            written.contains("keeper_seek_cooldown_ticks=" + KeeperRules.MIN_SEEK_COOLDOWN_TICKS), "the clamped value is written back: " + written
        );
    }

    @Test
    void aKeeperCooldownAboveTheMaximumIsClampedDownOnDiskToo(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "keeper_seek_cooldown_ticks=999999\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.MAX_SEEK_COOLDOWN_TICKS, VillagerCustomersConfig.keeperSeekCooldownTicks());
        String written = Files.readString(path);
        assertTrue(
            written.contains("keeper_seek_cooldown_ticks=" + KeeperRules.MAX_SEEK_COOLDOWN_TICKS), "the clamped value is written back: " + written
        );
    }

    @Test
    void aMalformedKeeperCooldownFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("villager_customers.properties");
        Files.writeString(path, "keeper_seek_cooldown_ticks=not-a-number\n");

        VillagerCustomersConfig.load(path);

        assertEquals(KeeperRules.DEFAULT_SEEK_COOLDOWN_TICKS, VillagerCustomersConfig.keeperSeekCooldownTicks());
    }
}
