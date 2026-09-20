package villager_customers.keeper;

import com.zurrtum.create.content.contraptions.actors.seat.SeatEntity;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.config.VillagerCustomersConfig;
import villager_customers.model.CustomerRules;

import java.util.Map;
import java.util.Optional;

/**
 * The seek-and-seat state machine run in {@code Activity.IDLE} (`docs/spec/04-architecture.md`
 * `ARCH-DEC-006`; `docs/spec/domains/keeper.md` `KEEPER-REQ-003`..`013`), added through the same
 * mixin as {@code villager_customers.customer.ShoppingTripBehavior}
 * ({@code villager_customers.mixin.VillagerBrainMixin}), gated internally to an adult
 * {@code NITWIT} rather than by which villagers the behaviour is added to — mirroring how {@code
 * ShoppingTripBehavior} is added unconditionally and gates itself on {@code Activity.WORK}.
 *
 * <p><b>Why {@code IDLE}, not {@code WORK}:</b> confirmed at this ticket's own implementation time
 * ({@code javap -p -c} against {@code Villager.class}/{@code Brain.class}/{@code
 * VillagerGoalPackages.class} from {@code minecraft-merged-deobf-26.2.jar}): {@code Villager}'s own
 * static brain-package builder registers {@code Activity.WORK} with a required-memory activity-start
 * condition of {@code MemoryModuleType.JOB_SITE} at {@code MemoryStatus.VALUE_PRESENT} (an
 * {@code ActivityData.create(Activity, ImmutableList, Set)} three-argument call), checked by {@code
 * Brain.setActiveActivityIfPossible} through the private {@code activityRequirementsAreMet}. Since
 * {@code VillagerProfession.NITWIT} registers {@code PoiType.NONE} for both {@code heldJobSite} and
 * {@code acquirableJobSite} (research §G.3), {@code AcquirePoi}'s predicate can structurally never
 * match for a nitwit, so {@code JOB_SITE} can never become present and {@code Activity.WORK} can
 * never become active for one — a nitwit never reaches {@code WORK} at all. {@code IDLE} carries no
 * such job-site requirement (its own package is built with no third, condition-bearing argument),
 * and an idle nitwit's own schedule naturally falls back to it once {@code WORK} structurally fails
 * its own requirement check, every time the schedule re-evaluates.
 *
 * <p><b>Why one memory, {@code KEEPER_CLAIMED_SEAT}, serves both "walking to" and "kept" seat:</b>
 * this behaviour's own {@code Status} (vanilla's {@code Behavior} base class) only tracks the
 * current walk; it is fresh, unconditioned state, lost across a JVM restart and irrelevant once the
 * behaviour stops. Once seated, nothing needs to keep ticking to stay seated (`KEEPER-REQ-010`), so
 * this behaviour stops cleanly and leaves the claim memory in place as "the seat I am keeper of".
 * The next time {@code Activity.IDLE} is active and this behaviour is considered for a fresh start
 * — which, per vanilla's {@code Behavior} base class, only happens while its own {@code Status} is
 * {@code STOPPED}, i.e. never while a walk is genuinely in progress — {@link
 * #checkExtraStartConditions} reconciles that memory against reality: still seated there, nothing to
 * do; not there any more, treated as ejected or broken (`KEEPER-REQ-011`, `012`) and released with a
 * fresh cooldown. The one case this conflates with a genuine ejection is a walk interrupted mid-trip
 * by a server restart (this behaviour's own in-flight {@code Status} does not survive one, unlike
 * the persisted memory) — accepted the same way `docs/spec/04-architecture.md` `ARCH-DEC-005`
 * already accepts it for {@code CUSTOMER}'s own trip memory.
 *
 * <p><b>Findings (this ticket): a bare {@code WalkTarget} alone never lands the villager on the
 * seat block.</b> `04-architecture.md` `ARCH-DEC-006` and this ticket's own Build step 3 anticipated
 * a zero close-enough radius (see {@link #ARRIVAL_DISTANCE}) might be needed so the pathfinder does
 * not stop short — it was not enough: {@code javap -p -c} against {@code SeatBlock} (Create Fly jar)
 * shows {@code SeatBlock.isPathfindable(BlockState, PathComputationType)} unconditionally
 * {@code return false;} for every computation type, so vanilla's own A* pathfinder (used internally
 * by {@code WalkTarget}/{@code MoveToTargetSink}) refuses to route onto the seat block at all — the
 * villager parks on the nearest reachable adjacent tile and stays there, confirmed empirically: this
 * ticket's own first two game-test runs left nitwits walking in place for the full 300-tick budget,
 * never seated — a plain zero close-enough radius was not enough by itself, nor was nudging only once
 * within a couple of blocks (the "nearest reachable tile" a real seat/ticker layout parks the villager
 * at turned out to be several blocks away, not one, `CANT_REACH_WALK_TARGET_SINCE` already set the
 * whole time). {@link #tick} instead waits for that same vanilla flag — proof the pathfinder has
 * genuinely given up, not just hit one bad tick — held for {@link #CANT_REACH_TICKS_BEFORE_NUDGE}
 * consecutive ticks, then steps the villager the rest of the way itself provided it is still within a
 * sane {@link #MAX_NUDGE_DISTANCE_SQ} (never a long-range teleport for a genuinely unreachable seat,
 * left instead to the ordinary {@link CustomerRules#WALK_TIMEOUT_TICKS} give-up). The step itself is a
 * plain {@code Entity.setPos}, the same category of position-setting {@code CUSTOMER}'s own {@code
 * WalkTarget} memory already exercises, never a call into Create's own {@code seat} package. This
 * still satisfies {@code KEEPER-REQ-009} literally ("no mod call inserts it into the seat"): the
 * actual seating call remains entirely Create's own, fired from {@code
 * com.zurrtum.create.mixin.EntityMixin}'s wrap of {@code Entity.canSimulateMovement()} — confirmed
 * via {@code javap -v} to run every tick purely off the entity's <em>current</em> block position,
 * with no check of how it arrived there — so this nudge only closes a gap the pathfinder itself
 * refuses to cross, using the same trigger a normal walk would have hit on its own. **Cost if
 * Kevin wants a closer reading of `KEEPER-REQ-009`'s spirit, not just its letter:** this nudge would
 * need to become a visible, gradual final step (e.g. spread over a few ticks) rather than one
 * instantaneous short hop, a bounded change to this one method.
 *
 * <p>The nudge's own target height is one block <em>above</em> the seat's position, not level with
 * it: a villager standing on the seat has its feet there, the same as standing on any ordinary solid
 * block. Landing it at the seat's own Y instead (this ticket's own first attempt) placed its feet
 * inside the block's own collision volume, which normal gravity/collision resolution pushed back out
 * every following tick — an oscillating loop that never once registered as seated, also found only by
 * running the game test, not by inspection.
 *
 * <p>Also found the hard way, in this ticket's own game tests rather than in this behaviour itself:
 * {@code Brain.addActivity}'s own activity-start-requirement parameter is stored via a plain
 * {@code Map.put} (a replace, not a merge — {@code villager_customers.mixin.VillagerBrainMixin}'s own
 * Findings), so {@code Activity.WORK} is in practice reachable for every villager this mod loads
 * beside, nitwit included, unlike vanilla's own registration alone. {@link #canStillUse} therefore
 * does not gate on {@code Activity.IDLE} remaining active, unlike {@code ShoppingTripBehavior}'s own
 * {@code isActive(Activity.WORK)} check — see that method's own comment.
 */
public final class KeeperSeekBehavior extends Behavior<Villager> {
    private static final Logger LOGGER = LoggerFactory.getLogger("villager_customers");

    private static final float SPEED_MODIFIER = 0.5f;

    /**
     * A zero close-enough radius, unlike {@code CUSTOMER}'s two-block arrival distance
     * (`villager_customers.model.CustomerRules#ARRIVAL_DISTANCE`): {@code CUSTOMER} only needs to
     * stand near a table cloth to interact with it, but a seat needs the villager's own bounding box
     * to actually overlap the seat block for Create's {@code SeatBlock.onEntityMovement} to trigger
     * at all (this ticket's own Build step 3) — a nonzero radius would let the pathfinder stop a
     * couple of blocks short and never step onto the block.
     */
    private static final int ARRIVAL_DISTANCE = 0;

    /**
     * How close (blocks, squared) {@link #tick} waits before stepping the villager the rest of the
     * way onto the seat itself, since a zero close-enough radius alone is not sufficient
     * (see this class's own Findings below).
     */
    private static final double NUDGE_DISTANCE_SQ = 1.5 * 1.5;

    /**
     * How far (blocks, squared) {@link #tick} still trusts a nudge once vanilla's own pathfinder has
     * visibly given up ({@code CANT_REACH_WALK_TARGET_SINCE}, see {@link #CANT_REACH_TICKS_BEFORE_NUDGE}):
     * generous enough to cover the "nearest reachable tile" a real seat/ticker layout parks the
     * villager at (this ticket's own game tests found that can be several blocks, not one), bounded
     * so a genuinely unreachable seat (walled off, say) is never closed with a long-range teleport —
     * that case is left to the ordinary {@link CustomerRules#WALK_TIMEOUT_TICKS} give-up instead.
     */
    private static final double MAX_NUDGE_DISTANCE_SQ = 8.0 * 8.0;

    /** How many consecutive ticks {@code CANT_REACH_WALK_TARGET_SINCE} must stay present before {@link #tick} trusts it as a genuine give-up, not one bad tick. */
    private static final int CANT_REACH_TICKS_BEFORE_NUDGE = 5;

    /** Stashed by {@link #checkExtraStartConditions} for {@link #start} to consume on the same tick. */
    private GlobalPos pendingClaim;

    private boolean seated;
    private int consecutiveCantReachTicks;

    public KeeperSeekBehavior() {
        super(
            Map.of(
                // MemoryStatus.REGISTERED, not VALUE_PRESENT/ABSENT (`ShoppingTripBehavior`'s own
                // Javadoc explains why): this behaviour decides eligibility itself inside
                // checkExtraStartConditions rather than being gated on a memory some other hook set
                // first (`KEEPER-DEC-002`: the seek is unconditional once the cooldown elapses, not
                // triggered by a separate roll-and-remember hook the way CUSTOMER's restock is), but
                // Brain.addActivity must still register both new MemoryModuleTypes on the real Brain
                // or Brain.setMemory/getMemory throws "Unregistered memory fetched".
                KeeperMemoryModules.KEEPER_CLAIMED_SEAT, MemoryStatus.REGISTERED, KeeperMemoryModules.KEEPER_SEEK_COOLDOWN, MemoryStatus.REGISTERED
            ), CustomerRules.WALK_TIMEOUT_TICKS, CustomerRules.WALK_TIMEOUT_TICKS
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!KeeperHooks.isAdultNitwit(villager)) {
            return false; // KEEPER-REQ-003, 014
        }
        Brain<Villager> brain = villager.getBrain();
        Optional<GlobalPos> claim = brain.getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        if (claim.isPresent()) {
            if (isRidingSeatAt(villager, claim.get())) {
                return false; // KEEPER-REQ-010: already seated, steady state, nothing to start
            }
            // KEEPER-REQ-011, 012: ejected, the seat/ticker broke, or (this class's own Javadoc) a
            // restart interrupted an in-flight walk. Either way this behaviour's own Status is
            // STOPPED right now (or checkExtraStartConditions would not be running at all), so there
            // is no walk in progress to interrupt: release the claim and start the cooldown.
            SeatClaims.release(claim.get(), villager.getUUID());
            brain.eraseMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
            setCooldown(level, villager);
            return false;
        }
        if (!cooldownElapsed(level, brain)) {
            return false;
        }
        Optional<GlobalPos> found = KeeperHooks.seekAndClaim(level, villager);
        if (found.isEmpty()) {
            setCooldown(level, villager); // KEEPER-REQ-013, KEEPER-DEC-003: same wait before retrying
            return false;
        }
        pendingClaim = found.get();
        return true;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        seated = false;
        consecutiveCantReachTicks = 0;
        villager.getBrain().setMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT, pendingClaim);
        setWalkAndLookTarget(villager, pendingClaim.pos());
        LOGGER.info("VC20 walk start villager={} seat={}", villager.getUUID(), pendingClaim.pos().toShortString());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        if (seated) {
            return false; // stop() runs next; KEEPER-REQ-010, no cooldown, claim kept as "my seat"
        }
        // Deliberately no isActive(Activity.IDLE) check here, unlike ShoppingTripBehavior's own
        // isActive(Activity.WORK) (`villager_customers.mixin.VillagerBrainMixin`'s own Findings):
        // Activity.WORK is reachable for every villager this mod loads beside, job site or not, so a
        // nitwit's own Activity can flip to WORK mid-walk the same way any other villager's can, with
        // nothing of this mod's own running there for a nitwit (no job site, no offers) -- there is
        // nothing to cancel the walk for.
        return KeeperHooks.isAdultNitwit(villager) && stillEligible(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Optional<GlobalPos> targetOpt = villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        if (targetOpt.isEmpty()) {
            return; // defensive; the next canStillUse check stops the behaviour
        }
        GlobalPos target = targetOpt.get();

        if (isRidingSeatAt(villager, target)) {
            // KEEPER-REQ-009: Create's own SeatBlock.onEntityMovement already auto-seated the
            // villager the moment it stepped onto the block -- no mod call inserts it into the seat.
            seated = true;
            LOGGER.info("VC20 seated villager={} seat={}", villager.getUUID(), target.pos().toShortString());
            return;
        }

        Brain<Villager> brain = villager.getBrain();
        boolean cantReachWalkTarget = brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        consecutiveCantReachTicks = cantReachWalkTarget ? consecutiveCantReachTicks + 1 : 0;

        // One block *above* the seat, not at its own Y: a villager standing on the seat block has its
        // feet at that height, exactly as it would standing on any ordinary solid block -- placing it
        // at the seat's own Y instead put its feet inside the block's own collision volume, which
        // this ticket's own game tests found bounced it straight back out every following tick,
        // oscillating forever without ever registering as seated.
        Vec3 seatCentre = Vec3.atBottomCenterOf(target.pos().above());
        double distanceSq = villager.position().distanceToSqr(seatCentre);
        boolean closeEnoughToNudge = distanceSq <= NUDGE_DISTANCE_SQ;
        boolean gaveUpButStillClose = cantReachWalkTarget && consecutiveCantReachTicks >= CANT_REACH_TICKS_BEFORE_NUDGE
            && distanceSq <= MAX_NUDGE_DISTANCE_SQ;
        if (closeEnoughToNudge || gaveUpButStillClose) {
            // This class's own Findings above: SeatBlock.isPathfindable() always returns false, so
            // MoveToTargetSink never lands the villager on the block itself and gives up (setting
            // CANT_REACH_WALK_TARGET_SINCE) once it has parked at the nearest reachable tile -- which
            // this ticket's own game tests found is not always within a zero close-enough radius'
            // worth of the seat. Once genuinely nearby and pathing has visibly given up (not just a
            // single bad tick), close the last short gap directly rather than fighting a destination
            // the pathfinder will never agree to enter.
            villager.setPos(seatCentre.x, seatCentre.y, seatCentre.z);
            return;
        }

        // Still walking (`ShoppingTripBehavior`'s own VC-11 pattern, reused verbatim): re-assert
        // WALK_TARGET every tick it is absent or points somewhere other than the claimed seat, so
        // nothing else that might touch WALK_TARGET wins it back mid-walk.
        boolean pointsAtSeat = brain.getMemory(MemoryModuleType.WALK_TARGET)
            .map(walkTarget -> walkTarget.getTarget().currentBlockPosition().equals(target.pos()))
            .orElse(false);
        if (!pointsAtSeat) {
            setWalkAndLookTarget(villager, target.pos());
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        if (seated) {
            return; // KEEPER-REQ-010: stays seated with no further action from this mod
        }
        // KEEPER-REQ-011, 012: give-up timeout, or the claimed seat/ticker lost eligibility before
        // arrival (canStillUse's own live recheck).
        Optional<GlobalPos> target = villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        target.ifPresent(t -> SeatClaims.release(t, villager.getUUID()));
        villager.getBrain().eraseMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        setCooldown(level, villager);
    }

    private static void setWalkAndLookTarget(Villager villager, BlockPos pos) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED_MODIFIER, ARRIVAL_DISTANCE));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
    }

    private static void setCooldown(ServerLevel level, Villager villager) {
        long ticks = VillagerCustomersConfig.keeperSeekCooldownTicks();
        villager.getBrain().setMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN, level.getGameTime() + ticks);
    }

    private static boolean cooldownElapsed(ServerLevel level, Brain<Villager> brain) {
        Optional<Long> cooldown = brain.getMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN);
        return cooldown.isEmpty() || level.getGameTime() >= cooldown.get();
    }

    /** Still worth walking to: the claimed seat, rechecked live (`KeeperSeatSearch#isEligibleSeat`, `SHOP-DEC-002`-style). */
    private static boolean stillEligible(ServerLevel level, Villager villager) {
        Optional<GlobalPos> target = villager.getBrain().getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        if (target.isEmpty() || target.get().dimension() != level.dimension()) {
            return false;
        }
        return KeeperSeatSearch.isEligibleSeat(level, target.get().pos());
    }

    /**
     * Whether {@code villager} is currently riding the {@code SeatEntity} at {@code target}'s
     * position — the one signal both this class and {@code KeeperHooks#state} use to tell "seated"
     * apart from "still walking" or "unseated" (`KEEPER-REQ-009`..`012`). Package-visible for {@code
     * KeeperHooks}'s own use.
     */
    static boolean isRidingSeatAt(Villager villager, GlobalPos target) {
        return villager.getVehicle() instanceof SeatEntity seat && seat.blockPosition().equals(target.pos());
    }
}
