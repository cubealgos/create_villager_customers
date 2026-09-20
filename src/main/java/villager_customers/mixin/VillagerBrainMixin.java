package villager_customers.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villager_customers.customer.CustomerHooks;
import villager_customers.customer.CustomerMemoryModules;
import villager_customers.customer.ShoppingTripBehavior;
import villager_customers.keeper.KeeperSeekBehavior;

import java.util.HashSet;
import java.util.Set;

/**
 * Wires the shopping trip and the nitwit keeper seek into a villager's brain, and rolls the restock
 * chance (`docs/spec/04-architecture.md` `ARCH-DEC-002`, `ARCH-DEC-006`;
 * `docs/spec/domains/customer.md` `CUSTOMER-REQ-001`, `002`; `docs/spec/domains/keeper.md`
 * `KEEPER-REQ-004`). The only mixin this mod carries into {@code minecraft} itself.
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
     * Vanilla priority slot 6 in {@code Activity.IDLE} too, also free of collision: {@code javap -p
     * -c} against {@code VillagerGoalPackages.getIdlePackage} shows outer priorities
     * {@code {1, 1, 1, 1, 2, 2, 3, 3, 3, 3, 99}} (`VC-20`'s own Findings; priority numbers only need
     * to avoid collision within the same {@code Activity}, not across different ones, so reusing the
     * digit {@code 6} from {@code WORK_PRIORITY} here is coincidence, not a shared constant).
     */
    private static final int IDLE_PRIORITY = 6;

    /**
     * Adds {@link ShoppingTripBehavior} to {@code Activity.WORK}'s priority set
     * (`CUSTOMER-REQ-001`) and {@link KeeperSeekBehavior} to {@code Activity.IDLE}'s
     * (`KEEPER-REQ-004`, `ARCH-DEC-006`). Injected at {@code RETURN}: both {@code makeBrain} and
     * {@code refreshBrain} call {@code Brain.Provider.makeBrain(...)} — which already builds every
     * vanilla activity, {@code WORK} and {@code IDLE} included, via the static
     * {@code VillagerGoalPackages} lists — before calling this method, so the brain passed in here
     * already has both activities registered and only needs one extra behaviour added to each
     * (`ARCH-DEC-002`; confirmed by disassembling {@code Villager.makeBrain}/{@code refreshBrain},
     * this ticket's own findings). {@code Brain.addActivity} is additive per activity
     * (`ARCH-FAIL-004`), so the two calls below coexist with each other and with vanilla's own
     * behaviours exactly as {@code ShoppingTripBehavior} alone already coexisted with them.
     *
     * <p>{@code SHOPPING_TRIP_TARGET} is also registered as a memory to erase when {@code WORK}
     * stops, as a defensive second guarantee alongside {@link ShoppingTripBehavior}'s own
     * cancellation handling (see that class's Javadoc for why the behaviour cannot rely on this
     * alone). {@code KEEPER_CLAIMED_SEAT} is deliberately <em>not</em> registered the same way for
     * {@code IDLE}: unlike a shopping trip, a claimed seat must survive the villager's schedule
     * moving on to another activity — most importantly once seated (`KEEPER-REQ-010`: "stays seated
     * day and night... the brain keeps running underneath but {@code SeatEntity} pins its
     * position"). Registering it here would have {@code Brain}'s own private
     * {@code eraseMemoriesForOtherActivitesThan} (confirmed by {@code javap -p -c} against
     * {@code Brain.setActiveActivity}/{@code eraseMemoriesForOtherActivitesThan}, called every time
     * the active activity changes) erase a seated nitwit's own claim the instant {@code REST} or
     * {@code MEET} next becomes active — {@link KeeperSeekBehavior} manages this memory's lifetime
     * entirely on its own instead (start/stop and its own {@code checkExtraStartConditions}
     * reconciliation), exactly the shape `KEEPER-REQ-011`/`012` need.
     *
     * <p><b>Fixed (`VC-21`, superseding `VC-20`'s own Findings paragraph above):</b> {@code
     * javap -p -c} against {@code Brain.addActivity} confirmed its third parameter (the
     * activity-start requirement) is stored via a plain, unconditional {@code
     * activityRequirements.put(activity, thatSet)} — a replace, not a merge — so
     * {@code CUSTOMER-REQ-001}'s call here used to pass {@code Set.of()} for {@code WORK} and wipe
     * vanilla's own requirement ({@code MemoryModuleType.JOB_SITE} at
     * {@code MemoryStatus.VALUE_PRESENT}, registered by {@code Villager.BRAIN_PROVIDER} before this
     * method ever runs) with nothing at all, letting a job-siteless villager — nitwits included —
     * into {@code WORK}, which vanilla never allows. Fixed by reading the brain's own
     * already-registered requirement and erase-on-stop sets for {@code WORK} through {@link
     * BrainActivityStateAccessor} and carrying them forward instead of replacing them, so this
     * mod's own re-registration is additive on every axis {@code Brain.addActivity} touches, not
     * just the behaviour set. {@code Activity.IDLE} needed no such fix and is left exactly as
     * `VC-20` wrote it: vanilla's own {@code IDLE} package is built through {@code ActivityData}'s
     * no-conditions overload (confirmed the same way), so it carries no requirement to begin with —
     * {@link KeeperSeekBehavior}'s own {@code Set.of()} here replaces nothing.
     *
     * <p><b>Verdict (`VC-21`, checked at the coordinator's request):</b> {@code refreshBrain} cannot
     * duplicate either behaviour — it never mutates the villager's existing {@code Brain} in place
     * (no {@code copyWithoutBehaviors()} exists in 26.2); it calls {@code stopAll} on the old one,
     * then builds an entirely new {@code Brain} via {@code BRAIN_PROVIDER.makeBrain(this,
     * oldBrain.pack())} — carrying over only memories, not behaviours — and only then calls this
     * method once against that fresh instance, exactly as {@code makeBrain} does (confirmed by
     * {@code javap -p -c} against {@code Villager.refreshBrain} and {@code Brain.pack}).
     */
    @Inject(method = "registerBrainGoals", at = @At("RETURN"))
    private void villager_customers$addShoppingTrip(Brain<Villager> brain, CallbackInfo ci) {
        BrainActivityStateAccessor accessor = (BrainActivityStateAccessor) brain;
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> workRequirements = new HashSet<>(
            accessor.villager_customers$activityRequirements().getOrDefault(Activity.WORK, Set.of())
        );
        Set<MemoryModuleType<?>> workEraseOnStop = new HashSet<>(
            accessor.villager_customers$activityMemoriesToEraseWhenStopped().getOrDefault(Activity.WORK, Set.of())
        );
        workEraseOnStop.add(CustomerMemoryModules.SHOPPING_TRIP_TARGET);

        brain.addActivity(
            Activity.WORK, ImmutableList.of(Pair.of(WORK_PRIORITY, new ShoppingTripBehavior())), workRequirements, workEraseOnStop
        );
        brain.addActivity(Activity.IDLE, ImmutableList.of(Pair.of(IDLE_PRIORITY, new KeeperSeekBehavior())), Set.of(), Set.of());
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
