package villager_customers.keeper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villager_customers.config.VillagerCustomersConfig;
import villager_customers.customer.CustomerHooks;
import villager_customers.model.CustomerRules;

import java.util.Optional;

/**
 * The claim-taking seek {@link KeeperSeekBehavior} runs, and the read-only state snapshot {@code
 * villager_customers.debug.DebugCommand}'s {@code keeper} subcommand reports
 * (`docs/spec/domains/keeper.md` `KEEPER-REQ-004`..`007`).
 */
public final class KeeperHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("villager_customers");

    private KeeperHooks() {
    }

    /** An adult (not baby) villager whose profession is {@code NITWIT} (`KEEPER-REQ-003`, `014`). */
    public static boolean isAdultNitwit(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.NITWIT);
    }

    /**
     * Finds the nearest eligible seat (`KEEPER-REQ-005`, `006`) and claims it (`KEEPER-REQ-007`) in
     * one step, trying the next-nearest candidate if a claim attempt loses a same-tick race. Empty
     * when nothing eligible is in range (`KEEPER-REQ-013`). The only method here that mutates
     * anything.
     */
    public static Optional<GlobalPos> seekAndClaim(ServerLevel level, Villager villager) {
        BlockPos origin = CustomerHooks.searchOrigin(level, villager);
        int radius = VillagerCustomersConfig.shopSearchRadius();
        long gameTime = level.getGameTime();
        long expiresAt = gameTime + CustomerRules.WALK_TIMEOUT_TICKS;

        for (GlobalPos candidate : KeeperSeatSearch.near(level, origin, radius, gameTime)) {
            if (SeatClaims.tryClaim(candidate, villager.getUUID(), gameTime, expiresAt)) {
                LOGGER.info("VC20 seek villager={} claimed={}", villager.getUUID(), candidate.pos().toShortString());
                return Optional.of(candidate);
            }
        }
        LOGGER.info("VC20 seek villager={} claimed=none origin={} radius={}", villager.getUUID(), origin.toShortString(), radius);
        return Optional.empty();
    }

    /**
     * The keeper state {@code villager_customers.debug.DebugCommand}'s {@code keeper} subcommand
     * reports: whether it is even a candidate at all, whether it is currently seated, its claimed
     * seat if any, and its cooldown value if any.
     */
    public static KeeperState state(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        Optional<GlobalPos> claimedSeat = brain.getMemory(KeeperMemoryModules.KEEPER_CLAIMED_SEAT);
        Optional<Long> cooldown = brain.getMemory(KeeperMemoryModules.KEEPER_SEEK_COOLDOWN);
        boolean seated = claimedSeat.isPresent() && KeeperSeekBehavior.isRidingSeatAt(villager, claimedSeat.get());
        return new KeeperState(isAdultNitwit(villager), seated, claimedSeat, cooldown);
    }

    /** Test-only: clears every seat claim, so one game test never sees another's leftovers. */
    public static void clearClaimsForTesting() {
        SeatClaims.clearForTesting();
    }

    /** A snapshot of one villager's keeper state, for {@code villager_customers.debug.DebugCommand}'s {@code keeper} subcommand. */
    public record KeeperState(boolean adultNitwit, boolean seated, Optional<GlobalPos> claimedSeat, Optional<Long> cooldownGameTime) {
    }
}
