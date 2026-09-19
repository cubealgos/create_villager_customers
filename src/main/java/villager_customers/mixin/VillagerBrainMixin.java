package villager_customers.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;
import villager_customers.customer.ShoppingTripBehavior;

import java.util.Set;

/**
 * Wires the shopping trip into a villager's brain and rolls the restock chance
 * (`docs/spec/04-architecture.md` `ARCH-DEC-002`; `docs/spec/domains/customer.md` `CUSTOMER-REQ-001`,
 * `002`). The only mixin this mod carries into {@code minecraft} itself.
 */
@Mixin(Villager.class)
final class VillagerBrainMixin {
    /**
     * Vanilla priority slot 6 in {@code Activity.WORK}: free of collision, verified by
     * {@code javap -p -c} against {@code VillagerGoalPackages.getWorkPackage}, whose own outer
     * priorities are {@code {2, 3, 5, 5, 10, 10, 99}} (job-site walk at 2, {@code GiveGiftToHero} at
     * 3, the minimal-look pair and the composter/POI/harvest/bonemeal {@code RunOne} both at 5,
     * {@code ShowTradesToPlayer} and {@code SetLookAndInteract} both at 10,
     * {@code UpdateActivityFromSchedule} at 99) — recorded in this ticket's own Findings.
     */
    private static final int WORK_PRIORITY = 6;

    /**
     * Adds {@link ShoppingTripBehavior} to {@code Activity.WORK}'s priority set
     * (`CUSTOMER-REQ-001`). Injected at {@code RETURN}: both {@code makeBrain} and
     * {@code refreshBrain} call {@code Brain.Provider.makeBrain(...)} — which already builds every
     * vanilla activity, {@code WORK} included, via the static {@code VillagerGoalPackages} lists —
     * before calling this method, so the brain passed in here already has {@code WORK} registered
     * and only needs this one extra behaviour added (`ARCH-DEC-002`; confirmed by disassembling
     * {@code Villager.makeBrain}/{@code refreshBrain}, this ticket's own findings).
     *
     * <p>{@code SHOPPING_TRIP_TARGET} is also registered as a memory to erase when {@code WORK}
     * stops, as a defensive second guarantee alongside {@link ShoppingTripBehavior}'s own
     * cancellation handling (see that class's Javadoc for why the behaviour cannot rely on this
     * alone).
     */
    @Inject(method = "registerBrainGoals", at = @At("RETURN"))
    private void villager_customers$addShoppingTrip(Brain<Villager> brain, CallbackInfo ci) {
        brain.addActivity(
            Activity.WORK, ImmutableList.of(Pair.of(WORK_PRIORITY, new ShoppingTripBehavior())), Set.of(),
            Set.of(CustomerMemoryModules.SHOPPING_TRIP_TARGET)
        );
    }

    /**
     * Rolls the restock chance exactly once per restock (`CUSTOMER-REQ-002`), narrowly at the point
     * {@code Villager.restock()} completes rather than wrapping or replacing the method itself
     * (`docs/spec/04-architecture.md`, this ticket's Approach).
     */
    @Inject(method = "restock", at = @At("RETURN"))
    private void villager_customers$onRestock(CallbackInfo ci) {
        CustomerHooks.onRestock((Villager) (Object) this);
    }
}
