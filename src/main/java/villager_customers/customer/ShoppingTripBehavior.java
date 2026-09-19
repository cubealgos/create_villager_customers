package villager_customers.customer;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import villager_customers.shop.Shop;
import villager_customers.transaction.TransactionExecutor;

import java.util.Map;
import java.util.Optional;

/**
 * The shopping-trip state machine run in {@code Activity.WORK} (`docs/spec/domains/customer.md` §3,
 * `CUSTOMER-REQ-001`, `004`..`009`): walks the villager to the shop remembered in
 * {@link CustomerMemoryModules#SHOPPING_TRIP_TARGET}, hands off to {@link TransactionExecutor} on
 * arrival, and cancels — clearing the memory and starting the cooldown — on panic, on leaving
 * {@code WORK}, on timeout, or when the target loses its shophood before arrival.
 *
 * <p>The 2,400-tick walk timeout (`CUSTOMER-REQ-004`) is the base {@link Behavior} class's own
 * {@code minDuration}/{@code maxDuration} machinery, fixed rather than randomised by passing the
 * same value for both: {@code timedOut(gameTime)} stops the behaviour on its own once
 * {@link CustomerRules#WALK_TIMEOUT_TICKS} ticks have passed since {@link #start}, with no extra
 * bookkeeping needed here.
 *
 * <p>Arrival vs. cancellation is told apart by a plain instance field rather than by whether
 * {@code SHOPPING_TRIP_TARGET} is still present at {@link #stop}: {@code Brain}'s own
 * {@code eraseMemoriesForOtherActivitesThan} (triggered by registering this memory in this
 * behaviour's {@code memoriesToEraseWhenStopped} set, see {@code VillagerBrainMixin}) can already
 * have erased it by the time {@link #stop} runs for a "left WORK" cancellation, which would
 * otherwise be indistinguishable from a successful arrival that cleared it itself.
 */
public final class ShoppingTripBehavior extends Behavior<Villager> {
    private static final float SPEED_MODIFIER = 0.5f;

    private boolean arrivedAndHandedOff;

    public ShoppingTripBehavior() {
        super(
            Map.of(
                CustomerMemoryModules.SHOPPING_TRIP_TARGET, MemoryStatus.VALUE_PRESENT,
                // MemoryStatus.REGISTERED, not VALUE_PRESENT/ABSENT: this behaviour never requires a
                // cooldown value to start, but Brain.addActivity registers each added behaviour's own
                // getRequiredMemories() into the villager's brain (private Brain.registerMemory,
                // confirmed via javap -p -c) — the only way a brand-new MemoryModuleType becomes
                // usable at all on a real Brain, whose memory slot map is otherwise fixed to
                // Villager's own hard-coded list. Without this, Brain.setMemory/getMemory on
                // SHOPPING_COOLDOWN throws "Unregistered memory fetched" (found by running this
                // ticket's own game tests).
                CustomerMemoryModules.SHOPPING_COOLDOWN, MemoryStatus.REGISTERED
            ), CustomerRules.WALK_TIMEOUT_TICKS, CustomerRules.WALK_TIMEOUT_TICKS
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return isEligible(level, villager);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        arrivedAndHandedOff = false;
        villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET).ifPresent(
            target -> villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target.pos(), SPEED_MODIFIER, CustomerRules.ARRIVAL_DISTANCE))
        );
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !arrivedAndHandedOff && isEligible(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Optional<GlobalPos> targetOpt = villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        if (targetOpt.isEmpty()) {
            return; // defensive; the next canStillUse check stops the behaviour
        }
        GlobalPos target = targetOpt.get();
        if (villager.blockPosition().distManhattan(target.pos()) > CustomerRules.ARRIVAL_DISTANCE) {
            return; // still walking
        }

        // Arrived (CUSTOMER-REQ-005): re-check shophood live, then run every one of the villager's
        // offers that matches, stopping each at its own first refusal (TransactionExecutor.execute
        // already does that per offer); an offer that no longer matches is simply skipped
        // (CUSTOMER-FAIL-002 — no offer still matching means nothing executed, no error).
        Shop.at(level, target.pos()).ifPresent(shop -> {
            for (MerchantOffer offer : villager.getOffers()) {
                TransactionExecutor.execute(level, villager, offer, shop);
            }
        });

        arrivedAndHandedOff = true;
        villager.getBrain().eraseMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        if (arrivedAndHandedOff) {
            return; // CUSTOMER-REQ-005: a successful arrival already cleared the memory; no cooldown
        }
        // Any other reason tickOrStop stopped us — panic, leaving WORK, timeout, or the target
        // losing its shophood — is a cancellation (CUSTOMER-REQ-006, 007; CUSTOMER-DEC-002): clear
        // the memory (idempotent even if Brain's own erase-on-stop already did it) and start the
        // cooldown so a cancelled trip cannot retry on the very next restock.
        villager.getBrain().eraseMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(CustomerMemoryModules.SHOPPING_COOLDOWN, gameTime + CustomerRules.COOLDOWN_TICKS);
    }

    /**
     * Still worth walking to: {@code WORK} active, not panicking, and the target — recomputed live,
     * never cached (`villager_customers.shop.Shop`'s own convention) — still has shophood.
     */
    private static boolean isEligible(ServerLevel level, Villager villager) {
        var brain = villager.getBrain();
        if (!brain.isActive(Activity.WORK) || brain.isActive(Activity.PANIC)) {
            return false;
        }
        Optional<GlobalPos> target = brain.getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        return target.isPresent() && target.get().dimension() == level.dimension() && Shop.at(level, target.get().pos()).isPresent();
    }
}
