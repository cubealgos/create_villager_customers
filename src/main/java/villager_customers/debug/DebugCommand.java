package villager_customers.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.zurrtum.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BlockEntity;
import villager_customers.VillagerCustomers;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;
import villager_customers.keeper.KeeperHooks;
import villager_customers.shop.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Development-only: {@code /villager_customers debug <roll|search|trip|box|shop> <...>} forces,
 * inspects or fully runs one villager's shopping trip against {@code villager_customers.customer}'s
 * real restock hook and search, or reports a nitwit's keeper state (`docs/spec/domains/keeper.md`,
 * `VC-20`) (`docs/spec/operations/testing.md`'s "Development tool" row,
 * `docs/spec/domains/customer.md` §3; `VC-5`). {@code roll} forces the next restock chance roll to
 * succeed; {@code search} runs the same eligible-offer, nearest-shop search read-only and prints the
 * result; {@code trip} skips the roll, runs the search, remembers the target and — forcing
 * {@code Activity.WORK} active first if the villager is not already working — lets the real
 * behaviour walk it there and trade, for screenshots and manual behaviour checks. {@code box}
 * (`VC-14`) prints every non-empty stack in the stock ticker's payment box at a position; {@code
 * shop} (`VC-14`) prints what {@link Shop#at} resolves for a table cloth at a position, or the first
 * reason it does not count as a shop — both for Kevin's live payment-box bug hunt. {@code keeper}
 * (`VC-20`) prints one villager's own keeper state: whether it is an adult nitwit at all, whether
 * it is currently seated, its claimed seat if any, and its cooldown game time if any. Registered
 * only when Fabric reports a development environment; never present in a released jar.
 */
public final class DebugCommand {
    private static final SimpleCommandExceptionType NOT_A_VILLAGER =
        new SimpleCommandExceptionType(Component.translatable("command.villager_customers.debug.not_a_villager"));

    private DebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
            Commands.literal(VillagerCustomers.MOD_ID).then(Commands.literal("debug")
                .then(Commands.literal("roll").then(Commands.argument("villager", EntityArgument.entity())
                    .executes(c -> roll(c.getSource(), villagerOf(c)))))
                .then(Commands.literal("search").then(Commands.argument("villager", EntityArgument.entity())
                    .executes(c -> search(c.getSource(), villagerOf(c)))))
                .then(Commands.literal("trip").then(Commands.argument("villager", EntityArgument.entity())
                    .executes(c -> trip(c.getSource(), villagerOf(c)))))
                .then(Commands.literal("box").then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(c -> box(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("shop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(c -> shop(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("keeper").then(Commands.argument("villager", EntityArgument.entity())
                    .executes(c -> keeper(c.getSource(), villagerOf(c))))))));
    }

    private static Villager villagerOf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "villager");
        if (!(entity instanceof Villager villager)) {
            throw NOT_A_VILLAGER.create();
        }
        return villager;
    }

    static int roll(CommandSourceStack source, Villager villager) {
        CustomerHooks.forceNextRoll(villager.getUUID());
        source.sendSuccess(() -> Component.translatable("command.villager_customers.debug.roll.done", villager.getDisplayName()), false);
        return 1;
    }

    static int search(CommandSourceStack source, Villager villager) {
        CustomerHooks.Search result = CustomerHooks.search(source.getLevel(), villager);
        reportSearchOutcome(source, villager, result);
        return result.reason() == CustomerHooks.Search.Reason.MATCH ? 1 : 0;
    }

    static int trip(CommandSourceStack source, Villager villager) {
        ServerLevel level = source.getLevel();
        CustomerHooks.Search result = CustomerHooks.search(level, villager);
        if (result.reason() == CustomerHooks.Search.Reason.MATCH) {
            Shop shop = result.shop().orElseThrow();
            villager.getBrain().setMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET, GlobalPos.of(level.dimension(), shop.pos()));
            if (!villager.getBrain().isActive(Activity.WORK)) {
                villager.getBrain().setActiveActivityIfPossible(Activity.WORK);
                source.sendSuccess(() -> Component.translatable("command.villager_customers.debug.trip.work_forced", villager.getDisplayName()), false);
            }
        }
        reportOutcome(
            source, villager, result, "command.villager_customers.debug.trip.match", "command.villager_customers.debug.trip.no_offer",
            "command.villager_customers.debug.trip.no_shop"
        );
        return result.reason() == CustomerHooks.Search.Reason.MATCH ? 1 : 0;
    }

    /**
     * {@code VC-14}: prints every non-empty stack in the stock ticker's payment box at {@code pos},
     * or that {@code pos} is not a stock ticker at all — server-side, for Kevin's live payment-box
     * bug hunt (`docs/spec` has no requirement of its own for this: dev tooling only).
     */
    static int box(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)) {
            source.sendSuccess(() -> Component.literal(pos.toShortString() + " is not a stock ticker"), false);
            return 0;
        }
        String text = pos.toShortString() + ": " + describeBox(ticker.getReceivedPaymentsHandler());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * {@code VC-14}: prints what {@link Shop#at} resolves for the table cloth at {@code pos} — its
     * ticker position, keeper presence, price and goods — reconstructing {@link Shop#at}'s own
     * checks one at a time (no block entity, not a cloth, no request, empty price, no ticker, no
     * keeper) so a non-match still says which one failed first.
     */
    static int shop(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        String posText = pos.toShortString();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return reportNotAShop(source, posText, "no block entity");
        }
        if (!(blockEntity instanceof TableClothBlockEntity cloth)) {
            return reportNotAShop(source, posText, "not a cloth");
        }
        if (!cloth.isShop()) {
            return reportNotAShop(source, posText, "no request");
        }
        ItemStack price = cloth.getPaymentItem();
        if (price.isEmpty() || cloth.getPaymentAmount() <= 0) {
            return reportNotAShop(source, posText, "empty price");
        }
        BlockPos tickerPos = pos.offset(cloth.requestData.targetOffset());
        BlockEntity tickerEntity = level.getBlockEntity(tickerPos);
        if (!(tickerEntity instanceof StockTickerBlockEntity ticker)) {
            return reportNotAShop(source, posText, "no ticker");
        }
        if (!ticker.isKeeperPresent()) {
            return reportNotAShop(source, posText, "no keeper");
        }
        String priceText = formatStack(price.copyWithCount(cloth.getPaymentAmount()));
        String goodsText = cloth.requestData.encodedRequest().stacks().stream()
            .map(big -> formatStack(big.stack.copyWithCount(big.count)))
            .collect(Collectors.joining(", "));
        String text = posText + ": shop -- ticker " + tickerPos.toShortString() + ", keeper present, price " + priceText + ", goods " + goodsText;
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * {@code VC-20}: prints {@code villager}'s own keeper state — whether it is an adult nitwit at
     * all, whether it is currently seated, its claimed seat if any, and its cooldown game time if
     * any (`docs/spec/domains/keeper.md`) — server-side, mirroring {@code box}/{@code shop}'s own
     * literal-text style (dynamic content, not a fixed message).
     */
    static int keeper(CommandSourceStack source, Villager villager) {
        ServerLevel level = source.getLevel();
        KeeperHooks.KeeperState state = KeeperHooks.state(level, villager);
        String text = villager.getUUID() + ": adultNitwit=" + state.adultNitwit() + ", seated=" + state.seated() + ", claimedSeat="
            + state.claimedSeat().map(pos -> pos.pos().toShortString()).orElse("none") + ", cooldownGameTime="
            + state.cooldownGameTime().map(String::valueOf).orElse("none") + " (now=" + level.getGameTime() + ")";
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int reportNotAShop(CommandSourceStack source, String posText, String reason) {
        source.sendSuccess(() -> Component.literal(posText + ": not a shop -- " + reason), false);
        return 0;
    }

    /** {@code VC-14}: every non-empty stack in {@code box}, formatted {@code count x id}, or "empty". */
    private static String describeBox(Container box) {
        List<String> stacks = new ArrayList<>();
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(formatStack(stack));
            }
        }
        return stacks.isEmpty() ? "empty" : String.join(", ", stacks);
    }

    /** {@code VC-14}: {@code stack} as {@code count x id}, e.g. {@code 1 x minecraft:emerald}. */
    private static String formatStack(ItemStack stack) {
        return stack.getCount() + " x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * Prints {@code result} through {@code source}: on a match, the shop's position, its distance
     * from {@code villager} in blocks, and the matched offer's cost and result (item ids and
     * counts); otherwise the reason category, using whichever of {@code matchKey}/{@code
     * noOfferKey}/{@code noShopKey} fits {@code search} or {@code trip}'s own wording.
     */
    private static void reportOutcome(
        CommandSourceStack source, Villager villager, CustomerHooks.Search result, String matchKey, String noOfferKey, String noShopKey
    ) {
        switch (result.reason()) {
            case MATCH -> {
                Shop shop = result.shop().orElseThrow();
                MerchantOffer offer = result.offer().orElseThrow();
                String posText = shop.pos().toShortString();
                double distance = Math.sqrt(villager.blockPosition().distSqr(shop.pos()));
                String distanceText = String.valueOf(Math.round(distance));
                String offerText = offerLabel(offer);
                source.sendSuccess(() -> Component.translatable(matchKey, villager.getDisplayName(), posText, distanceText, offerText), false);
            }
            case NO_OFFER_WITH_USES_LEFT -> source.sendSuccess(() -> Component.translatable(noOfferKey, villager.getDisplayName()), false);
            case NO_SHOP_MATCHING_OFFER -> source.sendSuccess(() -> Component.translatable(noShopKey, villager.getDisplayName()), false);
        }
    }

    /**
     * Prints {@code result} through {@code source} for the {@code search} subcommand specifically
     * (`VC-18`): unlike {@link #reportOutcome}'s shared match/no_offer/no_shop wording (also used by
     * {@code trip}), every line here also names the origin and radius the search actually used —
     * the village's meeting point or the villager's own position, and the configured
     * {@code shop_search_radius} — so a player or Kevin can see why a shop was or was not found
     * without reading the log.
     */
    private static void reportSearchOutcome(CommandSourceStack source, Villager villager, CustomerHooks.Search result) {
        String originText = result.origin().toShortString();
        String radiusText = String.valueOf(result.radius());
        switch (result.reason()) {
            case MATCH -> {
                Shop shop = result.shop().orElseThrow();
                MerchantOffer offer = result.offer().orElseThrow();
                String posText = shop.pos().toShortString();
                double distance = Math.sqrt(villager.blockPosition().distSqr(shop.pos()));
                String distanceText = String.valueOf(Math.round(distance));
                String offerText = offerLabel(offer);
                source.sendSuccess(
                    () -> Component.translatable(
                        "command.villager_customers.debug.search.match", villager.getDisplayName(), posText, distanceText, offerText, radiusText,
                        originText
                    ), false
                );
            }
            case NO_OFFER_WITH_USES_LEFT -> source.sendSuccess(
                () -> Component.translatable(
                    "command.villager_customers.debug.search.no_offer", villager.getDisplayName(), radiusText, originText
                ), false
            );
            case NO_SHOP_MATCHING_OFFER -> source.sendSuccess(
                () -> Component.translatable(
                    "command.villager_customers.debug.search.no_shop", villager.getDisplayName(), radiusText, originText
                ), false
            );
        }
    }

    private static String offerLabel(MerchantOffer offer) {
        return itemLabel(offer.getCostA()) + " -> " + itemLabel(offer.getResult());
    }

    private static String itemLabel(ItemStack stack) {
        return String.valueOf(stack.getCount()) + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
