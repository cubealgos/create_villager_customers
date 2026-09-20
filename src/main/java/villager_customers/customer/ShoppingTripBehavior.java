package villager_customers.customer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.model.CustomerRules;
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
 *
 * <p>{@code VC-11}: {@code WALK_TARGET} is a single, shared memory slot, and vanilla's own
 * {@code WORK} package keeps trying to write it too — {@code SetWalkTargetFromBlockMemory(JOB_SITE,
 * ...)} at priority 2 (this mixin's own trip behaviour sits at priority 6, `VC-4`'s Findings)
 * re-populates {@code WALK_TARGET} from {@code JOB_SITE} every tick it finds the memory absent, and
 * {@code MoveToTargetSink} (core, priority 1) erases it outright on arrival or a failed path
 * (confirmed for both via {@code javap -p -c} against {@code minecraft-merged-deobf-26.2.jar}). A
 * farmer standing right at its own composter is the worst case: {@link #start} alone (the previous
 * fix) only wins the very first tick after the memory is set — priority 2 runs first every tick, so
 * once anything empties {@code WALK_TARGET} mid-walk, the job site refills it before this behaviour
 * gets a chance to notice, and the villager never makes it to the shop even though the trip is still
 * "active" by every other memory. {@link #tick} now re-asserts {@code WALK_TARGET} (and
 * {@code LOOK_TARGET}) every tick the trip is still walking, whenever it is absent or points
 * somewhere other than the shop, so the trip always wins the slot back.
 */
public final class ShoppingTripBehavior extends Behavior<Villager> {
    /** {@code VC-14}: instrumentation for Kevin's live payment-box bug hunt, greppable as {@code VC14}. */
    private static final Logger LOGGER = LoggerFactory.getLogger("villager_customers");

    private static final float SPEED_MODIFIER = 0.5f;

    /**
     * How many consecutive ticks this behaviour tolerates {@code CANT_REACH_WALK_TARGET_SINCE}
     * staying present (vanilla's own "I couldn't path there" flag, set by {@code MoveToTargetSink})
     * before giving up early rather than fighting the work package every tick until the full
     * {@link CustomerRules#WALK_TIMEOUT_TICKS}-tick walk timeout: roughly {@code MoveToTargetSink}'s
     * own worst-case per-attempt retry backoff ({@code MoveToTargetSink()}'s default constructor
     * passes 150..250 ticks, confirmed via {@code javap -p -c}), doubled for margin, since one bad
     * attempt alone is not proof the shop is genuinely unreachable (`VC-11`'s own Findings).
     */
    private static final int UNREACHABLE_TICK_LIMIT = 400;

    private boolean arrivedAndHandedOff;
    private boolean unreachableTimedOut;
    private int consecutiveUnreachableTicks;

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
        unreachableTimedOut = false;
        consecutiveUnreachableTicks = 0;
        villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET).ifPresent(target -> setWalkAndLookTarget(villager, target.pos()));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !arrivedAndHandedOff && !unreachableTimedOut && isEligible(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Optional<GlobalPos> targetOpt = villager.getBrain().getMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        if (targetOpt.isEmpty()) {
            return; // defensive; the next canStillUse check stops the behaviour
        }
        GlobalPos target = targetOpt.get();
        if (villager.blockPosition().distManhattan(target.pos()) > CustomerRules.ARRIVAL_DISTANCE) {
            // Still walking (VC-11): the work package's own WALK_TARGET setters run at a lower
            // priority number than this behaviour (SetWalkTargetFromBlockMemory(JOB_SITE) at 2 vs.
            // this behaviour at 6, VC-4's Findings) and so are evaluated first every tick; whenever
            // one of them — or MoveToTargetSink erasing on a failed path — has left WALK_TARGET
            // absent or pointing anywhere but the shop, put it back before this tick ends.
            Brain<Villager> brain = villager.getBrain();
            boolean pointsAtShop = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> walkTarget.getTarget().currentBlockPosition().equals(target.pos()))
                .orElse(false);
            if (!pointsAtShop) {
                setWalkAndLookTarget(villager, target.pos());
            }

            // Bounded fight (VC-11): don't thrash a genuinely unreachable path every tick until the
            // full walk timeout. CANT_REACH_WALK_TARGET_SINCE staying present for UNREACHABLE_TICK_
            // LIMIT ticks running means MoveToTargetSink itself has kept failing to path there; give
            // up now through the existing cancel-and-cooldown path (canStillUse, then stop) instead.
            if (brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                consecutiveUnreachableTicks++;
                if (consecutiveUnreachableTicks >= UNREACHABLE_TICK_LIMIT) {
                    unreachableTimedOut = true;
                }
            } else {
                consecutiveUnreachableTicks = 0;
            }
            return;
        }

        // Arrived (CUSTOMER-REQ-005): re-check shophood live, then run every one of the villager's
        // offers that matches, stopping each at its own first refusal (TransactionExecutor.execute
        // already does that per offer); an offer that no longer matches is simply skipped
        // (CUSTOMER-FAIL-002 — no offer still matching means nothing executed, no error).
        Optional<Shop> shopAtArrival = Shop.at(level, target.pos());
        LOGGER.info(
            "VC14 trip arrival villager={} villagerPos={} target={} shopPresent={}", villager.getUUID(),
            villager.blockPosition().toShortString(), target.pos().toShortString(), shopAtArrival.isPresent()
        );
        shopAtArrival.ifPresent(shop -> {
            for (MerchantOffer offer : villager.getOffers()) {
                TransactionExecutor.execute(level, villager, offer, shop);
            }
        });

        arrivedAndHandedOff = true;
        villager.getBrain().eraseMemory(CustomerMemoryModules.SHOPPING_TRIP_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /** Points both {@code WALK_TARGET} and {@code LOOK_TARGET} at {@code pos} (VC-11's own Approach). */
    private static void setWalkAndLookTarget(Villager villager, BlockPos pos) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED_MODIFIER, CustomerRules.ARRIVAL_DISTANCE));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
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
