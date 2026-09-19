package villager_customers.gametest;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.BigItemStack;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import com.zurrtum.create.content.processing.burner.BlazeBurnerBlock;
import com.zurrtum.create.infrastructure.component.AutoRequestData;
import com.zurrtum.create.infrastructure.component.PackageOrderWithCrafts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec2;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;

/**
 * VC-5's three acceptance-criteria game tests for {@code villager_customers.debug.DebugCommand},
 * run in the game test environment, which is itself a development environment
 * (`FabricLoader.isDevelopmentEnvironment()` is true under {@code runGameTest}, exactly as it is
 * under {@code runClient}), so the command is registered and reachable here. Registration being
 * gated on that same check — the one line in {@code VillagerCustomers.onInitialize}, mirroring
 * {@code create_metered_motor}'s own `MM-8` — is otherwise proven by code review, not a game test:
 * there is no development/non-development pair of environments a single test run can compare
 * (`MM-8`'s own finding, "confirmed by code review noted in this ticket").
 *
 * <p>Every test runs the real command through {@code MinecraftServer.getCommands()
 * .performPrefixedCommand}, against a {@link CommandSourceStack} built with a capturing
 * {@link CommandSource} so the feedback {@code DebugCommand} sends can be asserted on directly, by
 * the translation key of the {@link TranslatableContents} it carries — robust to a dedicated
 * server's lack of client-side localisation, unlike asserting on the rendered English text.
 */
public final class DebugCommandGameTest {
    @GameTest(maxTicks = 60)
    public void searchPrintsAMatchAgainstARealShop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20);
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3)); // 4 blocks from the cloth
        villager.getOffers().add(freshOffer());

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            level.getServer().getCommands().performPrefixedCommand(source, "villager_customers debug search " + villager.getUUID());

            helper.assertTrue(
                capturing.hasKey("command.villager_customers.debug.search.match"),
                "search printed a match against the real shop: " + capturing.describe()
            );
            helper.assertTrue(
                !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "search is read-only: no trip memory was set"
            );
            helper.succeed();
        });
    }

    @GameTest
    public void rollSetsTheForcedFlag(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!CustomerHooks.hasForcedRoll(villager.getUUID()), "no forced roll is pending before the command runs");

            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "villager_customers debug roll " + villager.getUUID());

            helper.assertTrue(CustomerHooks.hasForcedRoll(villager.getUUID()), "the roll command set the forced flag: " + capturing.describe());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 300)
    public void tripEndsWithThePaymentBoxHoldingThePriceWithinTheTimeout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20); // enough stock for exactly one unit
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3));
        villager.getOffers().add(freshOffer());

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);
            helper.setTime(2000);

            // Deliberately never set an active activity here: this mod's mock villagers have no
            // profession or job site, so nothing but an explicit setActiveActivityIfPossible call
            // (ShoppingTripGameTest's own pattern) ever puts one into WORK — the trip command is the
            // only thing that does so below, proving CUSTOMER-REQ-001's "forced" claim for real.
            helper.assertTrue(!villager.getBrain().isActive(Activity.WORK), "the villager starts outside WORK");

            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            level.getServer().getCommands().performPrefixedCommand(source, "villager_customers debug trip " + villager.getUUID());

            helper.assertTrue(
                capturing.hasKey("command.villager_customers.debug.trip.work_forced"), "trip reported forcing WORK: " + capturing.describe()
            );
            helper.assertTrue(villager.getBrain().isActive(Activity.WORK), "trip forced WORK active");
            helper.assertTrue(
                villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "trip set the trip-target memory"
            );

            helper.succeedWhen(() -> helper.assertTrue(
                TransactionGameTest.paymentBoxHolds(network.ticker, Items.EMERALD, 1),
                "the forced trip walked the villager to the shop and traded: the payment box should hold one emerald"
            ));
        });
    }

    private static void configureCloth(GameTestHelper helper, BlockPos clothRelative, BlockPos tickerRelative) {
        BlockPos clothPos = helper.absolutePos(clothRelative);
        BlockPos tickerPos = helper.absolutePos(tickerRelative);
        TableClothBlockEntity cloth = helper.getBlockEntity(clothRelative, TableClothBlockEntity.class);
        cloth.priceTag.setFilter(new ItemStack(Items.EMERALD));
        cloth.priceTag.count = 1;
        cloth.requestData = new AutoRequestData(
            PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.WHEAT), 20))), "", tickerPos.subtract(clothPos), "", true
        );
    }

    private static MerchantOffer freshOffer() {
        return new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 2, 10, 0.0f);
    }

    /** A {@link CommandSourceStack} standing at {@code villager}'s position, feeding {@code capturing}. */
    private static CommandSourceStack sourceFor(GameTestHelper helper, CapturingSource capturing, Villager villager) {
        ServerLevel level = helper.getLevel();
        return new CommandSourceStack(
            capturing, villager.position(), Vec2.ZERO, level, PermissionSet.ALL_PERMISSIONS, "DebugCommandGameTest",
            Component.literal("DebugCommandGameTest"), level.getServer(), null
        );
    }

    /**
     * A {@link CommandSource} that records every message sent to it instead of delivering it
     * anywhere, so a game test can assert on {@code DebugCommand}'s feedback without a real player
     * — and by translation key rather than rendered text, since a dedicated server does not localise
     * ({@link TranslatableContents#getKey()} stays resolvable there even though {@code getString()}
     * would not reliably reflect {@code en_us.json}).
     */
    private static final class CapturingSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        boolean hasKey(String key) {
            return messages.stream().anyMatch(m -> m.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
        }

        String describe() {
            return messages.stream()
                .map(m -> m.getContents() instanceof TranslatableContents t ? t.getKey() : m.toString())
                .toList()
                .toString();
        }
    }
}
