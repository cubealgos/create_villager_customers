package villager_customers.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.VillagerCustomers;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;
import villager_customers.shop.Shop;

/**
 * Development-only: {@code /villager_customers debug <roll|search|trip> <villager>} forces,
 * inspects or fully runs one villager's shopping trip against {@code villager_customers.customer}'s
 * real restock hook and search (`docs/spec/operations/testing.md`'s "Development tool" row,
 * `docs/spec/domains/customer.md` §3; `VC-5`). {@code roll} forces the next restock chance roll to
 * succeed; {@code search} runs the same eligible-offer, nearest-shop search read-only and prints the
 * result; {@code trip} skips the roll, runs the search, remembers the target and — forcing
 * {@code Activity.WORK} active first if the villager is not already working — lets the real
 * behaviour walk it there and trade, for screenshots and manual behaviour checks. Registered only
 * when Fabric reports a development environment; never present in a released jar.
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
                    .executes(c -> trip(c.getSource(), villagerOf(c))))))));
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
        reportOutcome(
            source, villager, result, "command.villager_customers.debug.search.match", "command.villager_customers.debug.search.no_offer",
            "command.villager_customers.debug.search.no_shop"
        );
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

    private static String offerLabel(MerchantOffer offer) {
        return itemLabel(offer.getCostA()) + " -> " + itemLabel(offer.getResult());
    }

    private static String itemLabel(ItemStack stack) {
        return String.valueOf(stack.getCount()) + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
