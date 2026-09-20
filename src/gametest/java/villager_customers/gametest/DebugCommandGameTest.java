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
import net.minecraft.world.level.block.Blocks;
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
 *
 * <p>{@code VC-14}'s {@code box} and {@code shop} cases below are the exception: those two
 * subcommands print plain {@code Component.literal} text rather than a translated key (their
 * content — a live box listing, a live shop resolution — is inherently dynamic, not a fixed
 * message), so their own assertions check the rendered text directly through
 * {@link CapturingSource#hasText}, which is exactly as reliable as {@code getString()} for a
 * literal component (no locale lookup involved, unlike a translatable one).
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

    /**
     * VC-6 sweep gap: {@code CUSTOMER-REQ-009}/{@code CUSTOMER-FAIL-004}'s two idle-with-no-error
     * outcomes of a search were implemented (`CustomerHooks.Search.Reason`) but never exercised by a
     * test — only the {@code MATCH} path was. A freshly spawned villager has no profession and so no
     * offers at all (VC-4's own finding), so {@code search} reports {@code no_offer} with nothing
     * else set up.
     */
    @GameTest
    public void searchReportsNoOfferWithUsesLeft(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(2, () -> {
            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "villager_customers debug search " + villager.getUUID());

            helper.assertTrue(
                capturing.hasKey("command.villager_customers.debug.search.no_offer"),
                "a villager with no offers reports no offer with uses left: " + capturing.describe()
            );
            helper.assertTrue(
                !villager.getBrain().hasMemoryValue(CustomerMemoryModules.SHOPPING_TRIP_TARGET), "search is read-only: no trip memory was set"
            );
            helper.succeed();
        });
    }

    /**
     * VC-6 sweep gap, same finding as {@link #searchReportsNoOfferWithUsesLeft}: an eligible offer
     * with no reachable shop at all. {@code CustomerHooks.search} runs at the full production
     * {@code shop_search_radius} (default 128 blocks, `VC-18`; 48 at the time this test was written),
     * which — as {@code ShopSearchGameTest}'s own Javadoc documents — reaches into neighbouring
     * game-test structures roughly a dozen blocks away; most of this suite's shops share the same
     * wheat-for-emerald shape, so a plain
     * {@link #freshOffer()} here found one of theirs on first attempt (caught by this ticket's own
     * `just check` run, not by inspection). An offer shaped nothing like any shop this suite builds
     * — including {@code ShoppingTripGameTest}'s own diamond/netherite-ingot one, VC-6's other new
     * gap-closing test — avoids that false match regardless of which neighbours happen to be in
     * range.
     */
    @GameTest
    public void searchReportsNoShopMatchingOffer(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.getOffers().add(new MerchantOffer(new ItemCost(Items.GOLD_BLOCK, 3), new ItemStack(Items.EMERALD_BLOCK, 2), 2, 10, 0.0f));

        helper.runAfterDelay(2, () -> {
            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "villager_customers debug search " + villager.getUUID());

            helper.assertTrue(
                capturing.hasKey("command.villager_customers.debug.search.no_shop"),
                "an eligible offer with no reachable shop reports no shop matching: " + capturing.describe()
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
        BlockPos composterRelative = new BlockPos(1, 1, 1); // ShoppingTripGameTest.employAsFarmer's own convention

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3));

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);
            helper.setTime(2000);

            // VC-21: WORK now keeps vanilla's own JOB_SITE requirement, so the villager needs a real
            // job site before the trip command's own setActiveActivityIfPossible call (below, inside
            // DebugCommand.trip) can succeed at all -- employment alone does not activate WORK, so
            // the "starts outside WORK" assertion just below still holds.
            helper.setBlock(composterRelative, Blocks.COMPOSTER);
            ShoppingTripGameTest.employAsFarmer(helper, level, villager, helper.absolutePos(composterRelative));
            villager.getOffers().add(freshOffer()); // setVillagerData (inside employAsFarmer) nulls offers

            // Deliberately never set an active activity here: nothing but an explicit
            // setActiveActivityIfPossible call (ShoppingTripGameTest's own pattern) ever puts a
            // villager into WORK — the trip command is the only thing that does so below, proving
            // CUSTOMER-REQ-001's "forced" claim for real.
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

    /** {@code VC-14}: a real ticker with an emerald inserted straight into its payment box. */
    @GameTest
    public void boxListsEveryNonEmptyStackInTheTickersPaymentBox(GameTestHelper helper) {
        TestShopNetwork network = TestShopNetwork.build(helper, 0);
        BlockPos tickerRelative = new BlockPos(1, 1, 5); // TestShopNetwork.build's own ticker position
        BlockPos tickerPos = helper.absolutePos(tickerRelative);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        helper.runAfterDelay(2, () -> {
            network.ticker.receivedPayments.setItem(0, new ItemStack(Items.EMERALD, 1));

            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                source, "villager_customers debug box " + tickerPos.getX() + " " + tickerPos.getY() + " " + tickerPos.getZ()
            );

            helper.assertTrue(capturing.hasText("1 x minecraft:emerald"), "the box line lists the inserted emerald: " + capturing.describe());
            helper.succeed();
        });
    }

    /** {@code VC-14}: a cloth built with {@link TestShopNetwork}, the same real shop the other tests trade against. */
    @GameTest(maxTicks = 60)
    public void shopPrintsTheResolvedShopForARealCloth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestShopNetwork network = TestShopNetwork.build(helper, 20);
        BlockPos tickerRelative = new BlockPos(1, 1, 5);
        BlockPos keeperRelative = tickerRelative.east(1);
        BlockPos clothRelative = new BlockPos(1, 1, 7);
        BlockPos clothPos = helper.absolutePos(clothRelative);

        helper.setBlock(clothRelative, AllBlocks.ANDESITE_TABLE_CLOTH);
        helper.setBlock(
            keeperRelative, AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING)
        );
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 3));

        helper.runAfterDelay(3, () -> {
            configureCloth(helper, clothRelative, tickerRelative);

            CapturingSource capturing = new CapturingSource();
            CommandSourceStack source = sourceFor(helper, capturing, villager);
            level.getServer().getCommands().performPrefixedCommand(
                source, "villager_customers debug shop " + clothPos.getX() + " " + clothPos.getY() + " " + clothPos.getZ()
            );

            helper.assertTrue(
                capturing.hasText("keeper present") && capturing.hasText("1 x minecraft:emerald") && capturing.hasText("20 x minecraft:wheat"),
                "the shop line reports the resolved ticker, keeper presence, price and goods: " + capturing.describe()
            );
            helper.succeed();
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

        /** {@code VC-14}: whether any message's rendered text contains {@code substring} — safe for a
         * {@code Component.literal} message (no locale lookup involved), unlike a translatable one. */
        boolean hasText(String substring) {
            return messages.stream().anyMatch(m -> m.getString().contains(substring));
        }

        String describe() {
            return messages.stream()
                .map(m -> m.getContents() instanceof TranslatableContents t ? t.getKey() : m.toString())
                .toList()
                .toString();
        }
    }
}
